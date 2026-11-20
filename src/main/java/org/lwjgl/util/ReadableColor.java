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

import java.nio.ByteBuffer;







public interface ReadableColor {

	



	int getRed();

	



	int getGreen();

	



	int getBlue();

	



	int getAlpha();

	



	byte getRedByte();

	



	byte getGreenByte();

	



	byte getBlueByte();

	



	byte getAlphaByte();

	



	void writeRGBA(ByteBuffer dest);

	



	void writeRGB(ByteBuffer dest);

	



	void writeABGR(ByteBuffer dest);

	



	void writeBGR(ByteBuffer dest);

	



	void writeBGRA(ByteBuffer dest);

	



	void writeARGB(ByteBuffer dest);

	


	ReadableColor RED = new Color(255, 0, 0);
	ReadableColor ORANGE = new Color(255, 128, 0);
	ReadableColor YELLOW = new Color(255, 255, 0);
	ReadableColor GREEN = new Color(0, 255, 0);
	ReadableColor CYAN = new Color(0, 255, 255);
	ReadableColor BLUE = new Color(0, 0, 255);
	ReadableColor PURPLE = new Color(255, 0, 255);
	ReadableColor WHITE = new Color(255, 255, 255);
	ReadableColor BLACK = new Color(0, 0, 0);
	ReadableColor LTGREY = new Color(192, 192, 192);
	ReadableColor DKGREY = new Color(64, 64, 64);
	ReadableColor GREY = new Color(128, 128, 128);



}
