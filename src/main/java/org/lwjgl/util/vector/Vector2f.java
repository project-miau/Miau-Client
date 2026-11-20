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
package org.lwjgl.util.vector;

import java.io.Serializable;
import java.nio.FloatBuffer;











public class Vector2f extends Vector implements Serializable, ReadableVector2f, WritableVector2f {

	private static final long serialVersionUID = 1L;

	public float x, y;

	


	public Vector2f() {
		super();
	}

	


	public Vector2f(ReadableVector2f src) {
		set(src);
	}

	


	public Vector2f(float x, float y) {
		set(x, y);
	}

	


	public void set(float x, float y) {
		this.x = x;
		this.y = y;
	}

	




	public Vector2f set(ReadableVector2f src) {
		x = src.getX();
		y = src.getY();
		return this;
	}

	


	public float lengthSquared() {
		return x * x + y * y;
	}

	





	public Vector2f translate(float x, float y) {
		this.x += x;
		this.y += y;
		return this;
	}

	



	public Vector negate() {
		x = -x;
		y = -y;
		return this;
	}

	




	public Vector2f negate(Vector2f dest) {
		if (dest == null)
			dest = new Vector2f();
		dest.x = -x;
		dest.y = -y;
		return dest;
	}


	




	public Vector2f normalise(Vector2f dest) {
		float l = length();

		if (dest == null)
			dest = new Vector2f(x / l, y / l);
		else
			dest.set(x / l, y / l);

		return dest;
	}

	






	public static float dot(Vector2f left, Vector2f right) {
		return left.x * right.x + left.y * right.y;
	}



	





	public static float angle(Vector2f a, Vector2f b) {
		float dls = dot(a, b) / (a.length() * b.length());
		if (dls < -1f)
			dls = -1f;
		else if (dls > 1.0f)
			dls = 1.0f;
		return (float)Math.acos(dls);
	}

	







	public static Vector2f add(Vector2f left, Vector2f right, Vector2f dest) {
		if (dest == null)
			return new Vector2f(left.x + right.x, left.y + right.y);
		else {
			dest.set(left.x + right.x, left.y + right.y);
			return dest;
		}
	}

	







	public static Vector2f sub(Vector2f left, Vector2f right, Vector2f dest) {
		if (dest == null)
			return new Vector2f(left.x - right.x, left.y - right.y);
		else {
			dest.set(left.x - right.x, left.y - right.y);
			return dest;
		}
	}

	




	public Vector store(FloatBuffer buf) {
		buf.put(x);
		buf.put(y);
		return this;
	}

	




	public Vector load(FloatBuffer buf) {
		x = buf.get();
		y = buf.get();
		return this;
	}

	


	public Vector scale(float scale) {

		x *= scale;
		y *= scale;

		return this;
	}

	


	public String toString() {
		StringBuilder sb = new StringBuilder(64);

		sb.append("Vector2f[");
		sb.append(x);
		sb.append(", ");
		sb.append(y);
		sb.append(']');
		return sb.toString();
	}

	


	public final float getX() {
		return x;
	}

	


	public final float getY() {
		return y;
	}

	



	public final void setX(float x) {
		this.x = x;
	}

	



	public final void setY(float y) {
		this.y = y;
	}	
	
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Vector2f other = (Vector2f)obj;
		
		if (x == other.x && y == other.y) return true;
		
		return false;
	}
	
}
