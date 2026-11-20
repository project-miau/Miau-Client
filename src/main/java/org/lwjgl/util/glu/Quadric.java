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
package org.lwjgl.util.glu;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.*;








public class Quadric {

	protected int drawStyle;
	protected int orientation;
	protected boolean textureFlag;
	protected int normals;

	


	public Quadric() {
		super();

		drawStyle = GLU_FILL;
		orientation = GLU_OUTSIDE;
		textureFlag = false;
		normals = GLU_SMOOTH;
	}

	






	protected void normal3f(float x, float y, float z) {
	   float mag;

	   mag = (float)Math.sqrt(x * x + y * y + z * z);
	   if (mag > 0.00001F) {
	      x /= mag;
	      y /= mag;
	      z /= mag;
	   }
	   glNormal3f(x, y, z);
	}

	

















	public void setDrawStyle(int drawStyle) {
		this.drawStyle = drawStyle;
	}

    












	public void setNormals(int normals) {
		this.normals = normals;
	}

    












	public void setOrientation(int orientation) {
		this.orientation = orientation;
	}

    










	public void setTextureFlag(boolean textureFlag) {
		this.textureFlag = textureFlag;
	}


	



	public int getDrawStyle() {
		return drawStyle;
	}

	



	public int getNormals() {
		return normals;
	}

	



	public int getOrientation() {
		return orientation;
	}

	



	public boolean getTextureFlag() {
		return textureFlag;
	}

	protected void TXTR_COORD(float x, float y) {
		if (textureFlag) glTexCoord2f(x,y);
	}


	protected float sin(float r) {
		return (float)Math.sin(r);
	}

	protected float cos(float r) {
		return (float)Math.cos(r);
	}

}
