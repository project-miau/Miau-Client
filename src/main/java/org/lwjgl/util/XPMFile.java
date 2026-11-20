/*
 * Copyright (c) 2002-2008 LWJGL Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'LWJGL' nor the names of
 *   its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.lwjgl.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.StringTokenizer;












public class XPMFile {

	 
	private byte bytes[];

	private static final int WIDTH = 0;

	private static final int HEIGHT = 1;

	private static final int NUMBER_OF_COLORS = 2;

	private static final int CHARACTERS_PER_PIXEL = 3;

	private static int[] format = new int[4];

	


	private XPMFile() {
	}

	








	public static XPMFile load(String file) throws IOException {
		return load(new FileInputStream(new File(file)));
	}

	






	public static XPMFile load(InputStream is) {
		XPMFile xFile = new XPMFile();
		xFile.readImage(is);
		return xFile;
	}

	


	public int getHeight() {
		return format[HEIGHT];
	}

	


	public int getWidth() {
		return format[WIDTH];
	}

	


	public byte[] getBytes() {
		return bytes;
	}

	


	private void readImage(InputStream is) {
		try {
			LineNumberReader reader = new LineNumberReader(
					new InputStreamReader(is));
			HashMap<String, Integer> colors = new HashMap<String, Integer>();

			format = parseFormat(nextLineOfInterest(reader));

			
			for (int i = 0; i < format[NUMBER_OF_COLORS]; i++) {
				Object[] colorDefinition = parseColor(nextLineOfInterest(reader));
				colors.put((String)colorDefinition[0], (Integer)colorDefinition[1]);
			}

			
			bytes = new byte[format[WIDTH] * format[HEIGHT] * 4];
			for (int i = 0; i < format[HEIGHT]; i++) {
				parseImageLine(nextLineOfInterest(reader), format, colors, i);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Unable to parse XPM File");
		}
	}

	








	private static String nextLineOfInterest(LineNumberReader reader)
			throws IOException {
		String ret;
		do {
			ret = reader.readLine();
		} while (!ret.startsWith("\""));
		
		return ret.substring(1, ret.lastIndexOf('\"'));
	}

	






	private static int[] parseFormat(String format) {
		
		

		
		StringTokenizer st = new StringTokenizer(format);

		return new int[] { Integer.parseInt(st.nextToken()),  
		Integer.parseInt(st.nextToken()),  
		Integer.parseInt(st.nextToken()),  
		Integer.parseInt(st.nextToken())  
		};
	}

	







	private static Object[] parseColor(String line) {
		
		

		
		

		String key = line.substring(0, format[CHARACTERS_PER_PIXEL]);
		
		
		
		String color = line.substring(format[CHARACTERS_PER_PIXEL] + 4);

		
		return new Object[] { key, Integer.parseInt(color, 16) };
	}

	











	private void parseImageLine(String line, int[] format, HashMap<String, Integer> colors,
			int index) {
		
		int offset = index * 4 * format[WIDTH];

		
		
		for (int i = 0; i < format[WIDTH]; i++) {
			String key = line
					.substring(
							i * format[CHARACTERS_PER_PIXEL],
							(i * format[CHARACTERS_PER_PIXEL] + format[CHARACTERS_PER_PIXEL]));
			int color = colors.get(key);
			bytes[offset + (i * 4)] = (byte) ((color & 0x00ff0000) >> 16);
			bytes[offset + ((i * 4) + 1)] = (byte) ((color & 0x0000ff00) >> 8);
			bytes[offset + ((i * 4) + 2)] = (byte) ((color & 0x000000ff) >> 0); 
			
			
			bytes[offset + ((i * 4) + 3)] = (byte) 0xff; 
		}
	}

	


	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("usage:\nXPMFile <file>");
		}

		try {
			String out = args[0].substring(0, args[0].indexOf(".")) + ".raw";
			XPMFile file = XPMFile.load(args[0]);
			BufferedOutputStream bos = new BufferedOutputStream(
					new FileOutputStream(new File(out)));
			bos.write(file.getBytes());
			bos.close();

			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	





























}