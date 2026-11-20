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

import java.io.Serializable;







public final class Point implements ReadablePoint, WritablePoint, Serializable {

	static final long serialVersionUID = 1L;

	 
	private int x, y;

	


	public Point() {
		super();
	}

	


	public Point(int x, int y) {
		setLocation(x, y);
	}

	


	public Point(ReadablePoint p) {
		setLocation(p);
	}

	public void setLocation(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void setLocation(ReadablePoint p) {
		this.x = p.getX();
		this.y = p.getY();
	}
	
	public void setX(int x) {
		this.x = x;
	}
	
	public void setY(int y) {
		this.y = y;
	}
	
	




	public void translate(int dx, int dy) {
		this.x += dx;
		this.y += dy;
	}

	



	public void translate(ReadablePoint p) {
		this.x += p.getX();
		this.y += p.getY();
	}

	



	public void untranslate(ReadablePoint p) {
		this.x -= p.getX();
		this.y -= p.getY();
	}

	










	public boolean equals(Object obj) {
		if (obj instanceof Point) {
			Point pt = (Point) obj;
			return (x == pt.x) && (y == pt.y);
		}
		return super.equals(obj);
	}

	








	public String toString() {
		return getClass().getName() + "[x=" + x + ",y=" + y + "]";
	}
	
	




	public int hashCode() {
		int sum = x + y;
		return sum * (sum + 1) / 2 + x;
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}

	public void getLocation(WritablePoint dest) {
		dest.setLocation(x, y);
	}
	
}
