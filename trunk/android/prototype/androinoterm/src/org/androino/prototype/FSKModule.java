package org.androino.prototype;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Vector;

public class FSKModule {
	
	private static int BUFFER_SIZE	= 30000;
	private static int SAMPLING_FREQUENCY = 44100; //Hz
	private static double SAMPLING_TIME = 1.0/SAMPLING_FREQUENCY; //ms

	// reading zero+155(high)+1(low)+8bit+stop+end+zero
	private static int FREQUENCY_HIGH = 3150;
	private static int FREQUENCY_LOW = 1575;
	
	//high: 7 samples/peak
	//low : 14 samples/peak
	// 1492 samples/message low+8bits+stop+end 
	// 136 samples/bit  (=1492/11)
	private static int SAMPLES_PER_BIT = 136;
	
	private static int ENCODING_SAMPLES_PER_BIT = SAMPLES_PER_BIT/2; // 68

	// bit-high = 22 peaks
	// bit-low = 6 peaks
	private static int HIGH_BIT_N_PEAKS = 22;
	private static int LOW_BIT_N_PEAKS = 6;
	
	private static int SLOTS_PER_BIT = 4; // 4 parts: determines the size of the part analyzed to count peaks
	private static int N_POINTS = SAMPLES_PER_BIT/SLOTS_PER_BIT;  // 34=136/4
	
	private static double PEAK_AMPLITUDE_TRESHOLD = 5000; // significative sample (not noise)
	private static int NUMBER_SAMPLES_PEAK = 3;			// minimum number of significative samples to be considered a peak

	private static final int BIT_HIGH_SYMBOL=2;
	private static final int BIT_LOW_SYMBOL=1;
	private static final int BIT_NONE_SYMBOL=0;
	
	private static final int CARRIER_MIN_HIGH_BITS=12;
	
	private static final int SOUND_AMPLITUDE = 31000;
	
	private FSKModule(){
		
	}
	
	private static void debugInfo(String message){
		System.out.println(">>" + message);
	}

	public static double[] encode(int[] bits){
		FSKModule m = new FSKModule();
		return m.encodeMessage(bits);
	}
	                     
	private double[] encodeMessage(int[]bits){
		// reading zero+155(high)+1(low)+8bit+stop+end+zero
		double[] sound = new double[0];
		//generate zeros
		double[] zeros = new double[10*ENCODING_SAMPLES_PER_BIT];
		sound = concatenateArrays(sound,zeros);
		debugInfo("encodeMessage: zeros: nsamples=" + zeros.length);
		
		// generate carrier
		double duration = 28*ENCODING_SAMPLES_PER_BIT * SAMPLING_TIME; //experimental adjustment carrier duration = 28 bits 
		double[] carrier = generateTone(FREQUENCY_HIGH, duration);
		sound = concatenateArrays(sound,carrier);
		debugInfo("encodeMessage: carrier: nsamples=" + carrier.length);
		
		// generate message 
		duration = ENCODING_SAMPLES_PER_BIT * SAMPLING_TIME;
		// start-bit
		double[] message = new double[0];
		double[] bitArray = generateTone(FREQUENCY_LOW,duration);
		message = concatenateArrays(message, bitArray);
		// message bits
		for (int i = 0; i < bits.length; i++) {
			int freq = FREQUENCY_LOW;
			if ( bits[i]>1) freq = FREQUENCY_HIGH;
			bitArray = generateTone(freq,duration);
			message = concatenateArrays(message, bitArray);
		}
		sound = concatenateArrays(sound,message);
		debugInfo("encodeMessage: message: nsamples=" + message.length);
		
		// generate stop+end
		duration = duration*2;
		double[] end = generateTone(FREQUENCY_HIGH, duration);
		sound = concatenateArrays(sound,end);
		debugInfo("encodeMessage: end: nsamples=" + end.length);
		
		// zeros
		sound = concatenateArrays(sound,zeros);
		debugInfo("encodeMessage: sount total: nsamples=" + sound.length);
		return sound;
	}
	private double[] concatenateArrays(double[] a1, double[] a2){
		double[] data = new double[a1.length+a2.length];
		for (int i = 0; i < a1.length; i++) {
			data[i] = a1[i];
		}
		for (int i = 0; i < a2.length; i++) {
			data[i + a1.length] = a2[i];
		}
		return data;
	}
	
	private double[] generateTone(int frequency, double duration){
		//int duration = 1; // s
		int samplingRate = 44100; // Hz
		int numberOfSamples = (int)(duration * samplingRate);
		double samplingTime = 1.0 / samplingRate;
		samplingTime = 2* samplingTime ;
		
		double[] tone = new double[numberOfSamples];
		
		for (int i = 0; i < numberOfSamples; i++) {
			double y = Math.sin(2 * Math.PI * frequency * i * samplingTime);
			tone[i] = y * SOUND_AMPLITUDE;
		}
		return tone;
	}
	
	private void saveInfoToFile(String filePath, double[] data){
		String content = "";
		for (int i = 0; i < data.length; i++) {
			content+= ((int)data[i]) + "\n";
		}
        FileWriter fw;
		try {
			fw = new FileWriter(filePath, false);
	        fw.append(content);
	        fw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private double[] readInfoFromFile(String filePath){
		double[] data = new double[BUFFER_SIZE];
		int counter = 0;	                       
		try {
			File f = new File(filePath);
			FileReader fR = new FileReader(f);
			LineNumberReader lR = new LineNumberReader(fR);
			String line = lR.readLine(); //first line skipped
			do {
				line = lR.readLine();
				double d = Double.parseDouble(line);
				data[counter] = d;
				counter++;
				if (counter>=BUFFER_SIZE) break; 
			} while (line!=null );
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return data;
	}
	private Vector<String> decodeBits(int[] bits){
		// decodes the bits array and returns the messages
		Vector<String> msgs = new Vector<String>();
		decodeBits(bits,0,msgs);
		return msgs;
	}
	private void decodeBits(int[] bits, int startIndex, Vector<String> messages){
		// recursive decoding algorithm (finds start bit, get the message and call himself again)
		try {
			int index = findStartBit(bits, startIndex);
			String message = "";
			for (int i = 0; i < 8; i++) {
				message += bits[index+i];
			}
			messages.add(message);
			decodeBits(bits, index + 8 + 2, messages);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private int decodeUniqueMessage(int[] bits){
		// start bit
		int index = findStartBit(bits, 0);
		if (index == -1) return -1; // no start-bit detected
		if (index + 8 + 2 > bits.length)
			throw new AndroinoException("Message cutted, start bit at " + index, AndroinoException.TYPE_FSK_DECODING_ERROR);
		
		// 8bits message
		int value = 0;
		for (int i = 0; i < 8; i++) {
			int bit = bits[index+i];
			if (bit==BIT_HIGH_SYMBOL) value+=Math.pow(2, i);
		}
		// stop bit: do nothing
		// end bit: do nothing
		return value;
	}

	private int findStartBit(int[] bits, int startIndex){
		// find carrier and start bit
		int index = startIndex;
		int highCounter = 0;
		boolean startBitDetected = false;
		do {
			int bit = bits[index];
			switch (bit) {
			case BIT_HIGH_SYMBOL:
				highCounter++; // carrier high bit
				break;
			case BIT_LOW_SYMBOL:
				if (highCounter>CARRIER_MIN_HIGH_BITS) { // start-bit detected
					startBitDetected = true;
				}
				else highCounter = 0; // reset carrier counter
				break;
			case BIT_NONE_SYMBOL:
				highCounter = 0;// reset carrier counter
				break;
			}
			index++;
			if (index>=bits.length) return -1; 
		} while (!startBitDetected);
		return index;
	}
	
	private int[] parseBits(int[] peaks){
		// from the number of peaks array decode into an array of bits (2=bit-1, 1=bit-0, 0=no bit)
		// 
		int i =0;
		//int slots_per_bit = 4;
		int nBits = peaks.length /SLOTS_PER_BIT;
		int[] bits = new int[nBits];
		//i = findNextZero(peaks,i); // do not search for silence
		i = findNextNonZero(peaks,i);
		if (i+ SLOTS_PER_BIT >= peaks.length) //non-zero not found
			return bits;
		do {
			//int nPeaks = peaks[i]+peaks[i+1]+peaks[i+2]+peaks[i+3];
			int nPeaks = 0;
			for (int j = 0; j < SLOTS_PER_BIT; j++) {
				nPeaks+= peaks[i+j];
			}
			int position = i/SLOTS_PER_BIT;
			bits[position] = BIT_NONE_SYMBOL;
			
			if (nPeaks>LOW_BIT_N_PEAKS-2) bits[position] = BIT_LOW_SYMBOL;
			if (nPeaks>LOW_BIT_N_PEAKS+4) bits[position] = BIT_HIGH_SYMBOL;
			//if (nPeaks>5) bits[position] = 1;
			//if (nPeaks>12) bits[position] = 2;
			i=i+SLOTS_PER_BIT;
		} while (SLOTS_PER_BIT+i<peaks.length);
		return bits;
	}
	private int findNextNonZero(int[] peaks, int startIndex){
		// returns the position of the next value != 0 starting form startIndex
		int index = startIndex;
		int value = 1;
		do {
			value = peaks[index];
			index++;
		} while (value==0 && index<peaks.length-1);
		return index-1;
	}

	private int findNextZero(int[] peaks, int startIndex){
		// returns the position of the next value = 0 starting form startIndex
		int index = startIndex;
		int value = 1;
		do {
			value = peaks[index];
			index++;
		} while (value!=0 && index<peaks.length-1);
		return index-1;
	}
	
	private int[] processSound(double[] sound){
		// split the sound array into slots of N_POINTS and calculate the number of peaks
		
		int nPoints = N_POINTS;
		int nParts = sound.length / nPoints;
		int[] nPeaks = new int[nParts]; 
		int startIndex = 0;
		int i = 0;
		do {
			int endIndex = startIndex + nPoints;
			int n = this.countPeaks(sound, startIndex, endIndex);
			nPeaks[i] = n;
			i++;
			startIndex = endIndex;
		} while (i<nParts);
		//} while (startIndex+nPoints<sound.length);
		return nPeaks;
	}
	private int countPeaks(double[] sound, int startIndex, int endIndex){
		// count the number of peaks in the selected interval
		// peak identification criteria: sign changed and several significative samples (>PEAK_AMPLITUDE_TRESHOLD) 
		
		int index = startIndex;
		int signChangeCounter = 0;
		int numberSamplesGreaterThresdhold = 0;
		int sign = 0; // initialized at the first significant value
		do {
			double value = sound[index];
			if (Math.abs(value)>PEAK_AMPLITUDE_TRESHOLD) 
				numberSamplesGreaterThresdhold++; //significative value
			// sign initialization: take the sign of the first significant value
			if (sign==0 & numberSamplesGreaterThresdhold>0) sign = (int) (value / Math.abs(value));
			boolean signChanged = false;
			if (sign <0 & value >0)	signChanged = true;
			if (sign >0 & value <0)	signChanged = true;
			
			if (signChanged & numberSamplesGreaterThresdhold>NUMBER_SAMPLES_PEAK){
				signChangeCounter++; // count peak
				sign=-1*sign; //change sign
			}
			index++;
			//debugInfo(">>>>>>>index=" + index + " sign=" + sign + " signChangeCounter=" + signChangeCounter + " value=" + value + " numberSamplesGreaterThresdhold=" + numberSamplesGreaterThresdhold);
		} while (index<endIndex);
		return signChangeCounter;
	}
	
	public static int decodeSound(double[] sound){
		FSKModule m = new FSKModule();
		// processing sound in parts and 
		int[] nPeaks = m.processSound(sound);
		// transform number of peaks into bits 
		int[] bits = m.parseBits(nPeaks);
		// extract message from the bit array
		int message = m.decodeUniqueMessage(bits);
		return message;
	}
	
	
	public static void main(String[] args){
		FSKModule m = new FSKModule();
		//debugInfo("slot time(ms)=" + TIME_PER_SLOT);
		double[] sound;
		try {
			// reading info form file
			File f = new File("./");
			String path = f.getCanonicalPath();
			debugInfo("working dir=" + path);
			sound = m.readInfoFromFile("../testdata/sound.dat");
			for (int i = 0; i < 10; i++) {
				debugInfo("data:" + i + ":" + sound[i]);
			}

// -- Testing decoding calculation time
//Date startD = new Date();
//for (int k = 0; k < 1000; k++) {

			// processing sound
			int[] nPeaks = m.processSound(sound);
			for (int i = 0; i < nPeaks.length; i++) {
				debugInfo("nPeaks:" + i*N_POINTS + ":" + nPeaks[i]);
			}
			// transform number of peaks into bits 
			int[] bits = m.parseBits(nPeaks);
			for (int i = 0; i < bits.length; i++) {
				debugInfo("bits:" + i*N_POINTS*SLOTS_PER_BIT + ":" + bits[i]);
			}
			//for (int i = 0; i < bits.length; i++) {
			//	debugInfo("bits:" + i + ":" + bits[i]);
			//}
			// decode bits array into messages
			Vector<String> msgs = m.decodeBits(bits);
			for (Iterator iterator = msgs.iterator(); iterator.hasNext();) {
				String msg = (String) iterator.next();
				debugInfo("msg="+ msg);
			}
//}
//Date endD = new Date();
//System.out.println("calculation time=" + (endD.getTime()-startD.getTime()));
// Testing the processing time it takes 0.7 milliseconds

			// complete cycle: encode a message and then decode it.
			int[] message = {1,1,1,1,1,1,2,2}; //(2=bit-1, 1=bit-0,
			String msgText = "";
			for (int i = 0; i < message.length; i++) {
				msgText+= message[i];
			}
			debugInfo("encoded message=" + msgText);
			double[] soundA = m.encodeMessage(message);
			//m.saveInfoToFile("./testdata/generated.dat", soundA);
			int[] nPeaksA = m.processSound(soundA);
			int[] bitsA = m.parseBits(nPeaksA);
			Vector<String> messages = m.decodeBits(bitsA);
			for (Iterator iterator = messages.iterator(); iterator.hasNext();) {
				String msg = (String) iterator.next();
				debugInfo("msg="+ msg);
			}
			
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

}