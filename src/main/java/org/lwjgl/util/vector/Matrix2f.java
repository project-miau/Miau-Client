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










public class Matrix2f extends Matrix implements Serializable {

	private static final long serialVersionUID = 1L;

	public float m00, m01, m10, m11;

	


	public Matrix2f() {
		setIdentity();
	}

	


	public Matrix2f(Matrix2f src) {
		load(src);
	}

	




	public Matrix2f load(Matrix2f src) {
		return load(src, this);
	}

	





	public static Matrix2f load(Matrix2f src, Matrix2f dest) {
		if (dest == null)
			dest = new Matrix2f();

		dest.m00 = src.m00;
		dest.m01 = src.m01;
		dest.m10 = src.m10;
		dest.m11 = src.m11;

		return dest;
	}

	






	public Matrix load(FloatBuffer buf) {

		m00 = buf.get();
		m01 = buf.get();
		m10 = buf.get();
		m11 = buf.get();

		return this;
	}

	






	public Matrix loadTranspose(FloatBuffer buf) {

		m00 = buf.get();
		m10 = buf.get();
		m01 = buf.get();
		m11 = buf.get();

		return this;
	}

	




	public Matrix store(FloatBuffer buf) {
		buf.put(m00);
		buf.put(m01);
		buf.put(m10);
		buf.put(m11);
		return this;
	}

	




	public Matrix storeTranspose(FloatBuffer buf) {
		buf.put(m00);
		buf.put(m10);
		buf.put(m01);
		buf.put(m11);
		return this;
	}



	






	public static Matrix2f add(Matrix2f left, Matrix2f right, Matrix2f dest) {
		if (dest == null)
			dest = new Matrix2f();

		dest.m00 = left.m00 + right.m00;
		dest.m01 = left.m01 + right.m01;
		dest.m10 = left.m10 + right.m10;
		dest.m11 = left.m11 + right.m11;

		return dest;
	}

	






	public static Matrix2f sub(Matrix2f left, Matrix2f right, Matrix2f dest) {
		if (dest == null)
			dest = new Matrix2f();

		dest.m00 = left.m00 - right.m00;
		dest.m01 = left.m01 - right.m01;
		dest.m10 = left.m10 - right.m10;
		dest.m11 = left.m11 - right.m11;

		return dest;
	}

	






	public static Matrix2f mul(Matrix2f left, Matrix2f right, Matrix2f dest) {
		if (dest == null)
			dest = new Matrix2f();

		float m00 = left.m00 * right.m00 + left.m10 * right.m01;
		float m01 = left.m01 * right.m00 + left.m11 * right.m01;
		float m10 = left.m00 * right.m10 + left.m10 * right.m11;
		float m11 = left.m01 * right.m10 + left.m11 * right.m11;

		dest.m00 = m00;
		dest.m01 = m01;
		dest.m10 = m10;
		dest.m11 = m11;

		return dest;
	}

	







	public static Vector2f transform(Matrix2f left, Vector2f right, Vector2f dest) {
		if (dest == null)
			dest = new Vector2f();

		float x = left.m00 * right.x + left.m10 * right.y;
		float y = left.m01 * right.x + left.m11 * right.y;

		dest.x = x;
		dest.y = y;

		return dest;
	}

	



	public Matrix transpose() {
		return transpose(this);
	}

	




	public Matrix2f transpose(Matrix2f dest) {
		return transpose(this, dest);
	}

	





	public static Matrix2f transpose(Matrix2f src, Matrix2f dest) {
		if (dest == null)
			dest = new Matrix2f();

		float m01 = src.m10;
		float m10 = src.m01;

		dest.m01 = m01;
		dest.m10 = m10;

		return dest;
	}

	



	public Matrix invert() {
		return invert(this, this);
	}

	





	public static Matrix2f invert(Matrix2f src, Matrix2f dest) {
		



		float determinant = src.determinant();
		if (determinant != 0) {
			if (dest == null)
				dest = new Matrix2f();
			float determinant_inv = 1f/determinant;
			float t00 =  src.m11*determinant_inv;
			float t01 = -src.m01*determinant_inv;
			float t11 =  src.m00*determinant_inv;
			float t10 = -src.m10*determinant_inv;

			dest.m00 = t00;
			dest.m01 = t01;
			dest.m10 = t10;
			dest.m11 = t11;
			return dest;
		} else
			return null;
	}

	


	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(m00).append(' ').append(m10).append(' ').append('\n');
		buf.append(m01).append(' ').append(m11).append(' ').append('\n');
		return buf.toString();
	}

	



	public Matrix negate() {
		return negate(this);
	}

	




	public Matrix2f negate(Matrix2f dest) {
		return negate(this, dest);
	}

	





	public static Matrix2f negate(Matrix2f src, Matrix2f dest) {
		if (dest == null)
			dest = new Matrix2f();

		dest.m00 = -src.m00;
		dest.m01 = -src.m01;
		dest.m10 = -src.m10;
		dest.m11 = -src.m11;

		return dest;
	}

	



	public Matrix setIdentity() {
		return setIdentity(this);
	}

	




	public static Matrix2f setIdentity(Matrix2f src) {
		src.m00 = 1.0f;
		src.m01 = 0.0f;
		src.m10 = 0.0f;
		src.m11 = 1.0f;
		return src;
	}

	



	public Matrix setZero() {
		return setZero(this);
	}

	public static Matrix2f setZero(Matrix2f src) {
		src.m00 = 0.0f;
		src.m01 = 0.0f;
		src.m10 = 0.0f;
		src.m11 = 0.0f;
		return src;
	}

	


	public float determinant() {
		return m00 * m11 - m01*m10;
	}
}
