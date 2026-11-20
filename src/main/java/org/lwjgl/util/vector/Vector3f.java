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











public class Vector3f extends Vector implements Serializable, ReadableVector3f, WritableVector3f {

	private static final long serialVersionUID = 1L;

	public float x, y, z;

	


	public Vector3f() {
		super();
	}

	


	public Vector3f(ReadableVector3f src) {
		set(src);
	}

	


	public Vector3f(float x, float y, float z) {
		set(x, y, z);
	}

	


	public void set(float x, float y) {
		this.x = x;
		this.y = y;
	}

	


	public void set(float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	




	public Vector3f set(ReadableVector3f src) {
		x = src.getX();
		y = src.getY();
		z = src.getZ();
		return this;
	}

	


	public float lengthSquared() {
		return x * x + y * y + z * z;
	}

	





	public Vector3f translate(float x, float y, float z) {
		this.x += x;
		this.y += y;
		this.z += z;
		return this;
	}

	







	public static Vector3f add(Vector3f left, Vector3f right, Vector3f dest) {
		if (dest == null)
			return new Vector3f(left.x + right.x, left.y + right.y, left.z + right.z);
		else {
			dest.set(left.x + right.x, left.y + right.y, left.z + right.z);
			return dest;
		}
	}

	







	public static Vector3f sub(Vector3f left, Vector3f right, Vector3f dest) {
		if (dest == null)
			return new Vector3f(left.x - right.x, left.y - right.y, left.z - right.z);
		else {
			dest.set(left.x - right.x, left.y - right.y, left.z - right.z);
			return dest;
		}
	}

	







	public static Vector3f cross(
			Vector3f left,
			Vector3f right,
			Vector3f dest)
	{

		if (dest == null)
			dest = new Vector3f();

		dest.set(
				left.y * right.z - left.z * right.y,
				right.x * left.z - right.z * left.x,
				left.x * right.y - left.y * right.x
				);

		return dest;
	}



	



	public Vector negate() {
		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	




	public Vector3f negate(Vector3f dest) {
		if (dest == null)
			dest = new Vector3f();
		dest.x = -x;
		dest.y = -y;
		dest.z = -z;
		return dest;
	}


	




	public Vector3f normalise(Vector3f dest) {
		float l = length();

		if (dest == null)
			dest = new Vector3f(x / l, y / l, z / l);
		else
			dest.set(x / l, y / l, z / l);

		return dest;
	}

	






	public static float dot(Vector3f left, Vector3f right) {
		return left.x * right.x + left.y * right.y + left.z * right.z;
	}

	





	public static float angle(Vector3f a, Vector3f b) {
		float dls = dot(a, b) / (a.length() * b.length());
		if (dls < -1f)
			dls = -1f;
		else if (dls > 1.0f)
			dls = 1.0f;
		return (float)Math.acos(dls);
	}

	


	public Vector load(FloatBuffer buf) {
		x = buf.get();
		y = buf.get();
		z = buf.get();
		return this;
	}

	


	public Vector scale(float scale) {

		x *= scale;
		y *= scale;
		z *= scale;

		return this;

	}

	


	public Vector store(FloatBuffer buf) {

		buf.put(x);
		buf.put(y);
		buf.put(z);

		return this;
	}

	


	public String toString() {
		StringBuilder sb = new StringBuilder(64);

		sb.append("Vector3f[");
		sb.append(x);
		sb.append(", ");
		sb.append(y);
		sb.append(", ");
		sb.append(z);
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

	



	public void setZ(float z) {
		this.z = z;
	}

	


	public float getZ() {
		return z;
	}
	
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Vector3f other = (Vector3f)obj;
		
		if (x == other.x && y == other.y && z == other.z) return true;
		
		return false;
	}
}
