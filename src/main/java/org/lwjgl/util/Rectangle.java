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







public final class Rectangle implements ReadableRectangle, WritableRectangle, Serializable {

	static final long serialVersionUID = 1L;

	 
	private int x, y, width, height;

	


	public Rectangle() {
		super();
	}
	


	public Rectangle(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.width = w;
		this.height = h;
	}
	


	public Rectangle(ReadablePoint p, ReadableDimension d) {
		x = p.getX();
		y = p.getY();
		width = d.getWidth();
		height = d.getHeight();
	}
	


	public Rectangle(ReadableRectangle r) {
		x = r.getX();
		y = r.getY();
		width = r.getWidth();
		height = r.getHeight();
	}

	public void setLocation(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void setLocation(ReadablePoint p) {
		this.x = p.getX();
		this.y = p.getY();
	}

	public void setSize(int w, int h) {
		this.width = w;
		this.height = h;
	}

	public void setSize(ReadableDimension d) {
		this.width = d.getWidth();
		this.height = d.getHeight();
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.width = w;
		this.height = h;
	}

	public void setBounds(ReadablePoint p, ReadableDimension d) {
		x = p.getX();
		y = p.getY();
		width = d.getWidth();
		height = d.getHeight();
	}

	public void setBounds(ReadableRectangle r) {
		x = r.getX();
		y = r.getY();
		width = r.getWidth();
		height = r.getHeight();
	}
	
	


	public void getBounds(WritableRectangle dest) {
		dest.setBounds(x, y, width, height);
	}
	
	


	public void getLocation(WritablePoint dest) {
		dest.setLocation(x, y);
	}
	
	


	public void getSize(WritableDimension dest) {
		dest.setSize(width, height);
	}

	




	public void translate(int x, int y) {
		this.x += x;
		this.y += y;
	}

	



	public void translate(ReadablePoint point) {
		this.x += point.getX();
		this.y += point.getY();
	}

	



	public void untranslate(ReadablePoint point) {
		this.x -= point.getX();
		this.y -= point.getY();
	}

	








	public boolean contains(ReadablePoint p) {
		return contains(p.getX(), p.getY());
	}

	










	public boolean contains(int X, int Y) {
		int w = this.width;
		int h = this.height;
		if ((w | h) < 0) {
			
			return false;
		}
		
		int x = this.x;
		int y = this.y;
		if (X < x || Y < y) {
			return false;
		}
		w += x;
		h += y;
		
		return ((w < x || w > X) && (h < y || h > Y));
	}

	







	public boolean contains(ReadableRectangle r) {
		return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight());
	}

	













	public boolean contains(int X, int Y, int W, int H) {
		int w = this.width;
		int h = this.height;
		if ((w | h | W | H) < 0) {
			
			return false;
		}
		
		int x = this.x;
		int y = this.y;
		if (X < x || Y < y) {
			return false;
		}
		w += x;
		W += X;
		if (W <= X) {
			
			
			
			
			if (w >= x || W > w)
				return false;
		} else {
			
			
			
			if (w >= x && W > w)
				return false;
		}
		h += y;
		H += Y;
		if (H <= Y) {
			if (h >= y || H > h)
				return false;
		} else {
			if (h >= y && H > h)
				return false;
		}
		return true;
	}

	









	public boolean intersects(ReadableRectangle r) {
		int tw = this.width;
		int th = this.height;
		int rw = r.getWidth();
		int rh = r.getHeight();
		if (rw <= 0 || rh <= 0 || tw <= 0 || th <= 0) {
			return false;
		}
		int tx = this.x;
		int ty = this.y;
		int rx = r.getX();
		int ry = r.getY();
		rw += rx;
		rh += ry;
		tw += tx;
		th += ty;
		
		return ((rw < rx || rw > tx) && (rh < ry || rh > ty) && (tw < tx || tw > rx) && (th < ty || th > ry));
	}

	












	public Rectangle intersection(ReadableRectangle r, Rectangle dest) {
		int tx1 = this.x;
		int ty1 = this.y;
		int rx1 = r.getX();
		int ry1 = r.getY();
		long tx2 = tx1;
		tx2 += this.width;
		long ty2 = ty1;
		ty2 += this.height;
		long rx2 = rx1;
		rx2 += r.getWidth();
		long ry2 = ry1;
		ry2 += r.getHeight();
		if (tx1 < rx1)
			tx1 = rx1;
		if (ty1 < ry1)
			ty1 = ry1;
		if (tx2 > rx2)
			tx2 = rx2;
		if (ty2 > ry2)
			ty2 = ry2;
		tx2 -= tx1;
		ty2 -= ty1;
		
		
		
		if (tx2 < Integer.MIN_VALUE)
			tx2 = Integer.MIN_VALUE;
		if (ty2 < Integer.MIN_VALUE)
			ty2 = Integer.MIN_VALUE;
		if (dest == null)
			dest = new Rectangle(tx1, ty1, (int) tx2, (int) ty2);
		else
			dest.setBounds(tx1, ty1, (int) tx2, (int) ty2);
		return dest;
		
	}

	









	public WritableRectangle union(ReadableRectangle r, WritableRectangle dest) {
		int x1 = Math.min(x, r.getX());
		int x2 = Math.max(x + width, r.getX() + r.getWidth());
		int y1 = Math.min(y, r.getY());
		int y2 = Math.max(y + height, r.getY() + r.getHeight());
		dest.setBounds(x1, y1, x2 - x1, y2 - y1);
		return dest;
	}

	

















	public void add(int newx, int newy) {
		int x1 = Math.min(x, newx);
		int x2 = Math.max(x + width, newx);
		int y1 = Math.min(y, newy);
		int y2 = Math.max(y + height, newy);
		x = x1;
		y = y1;
		width = x2 - x1;
		height = y2 - y1;
	}

	

















	public void add(ReadablePoint pt) {
		add(pt.getX(), pt.getY());
	}

	





	public void add(ReadableRectangle r) {
		int x1 = Math.min(x, r.getX());
		int x2 = Math.max(x + width, r.getX() + r.getWidth());
		int y1 = Math.min(y, r.getY());
		int y2 = Math.max(y + height, r.getY() + r.getHeight());
		x = x1;
		y = y1;
		width = x2 - x1;
		height = y2 - y1;
	}

	






















	public void grow(int h, int v) {
		x -= h;
		y -= v;
		width += h * 2;
		height += v * 2;
	}

	






	public boolean isEmpty() {
		return (width <= 0) || (height <= 0);
	}
	










	public boolean equals(Object obj) {
		if (obj instanceof Rectangle) {
			Rectangle r = (Rectangle) obj;
			return ((x == r.x) && (y == r.y) && (width == r.width) && (height == r.height));
		}
		return super.equals(obj);
	}

	



	public String toString() {
		return getClass().getName() + "[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]";
	}
	



	public int getHeight() {
		return height;
	}

	



	public void setHeight(int height) {
		this.height = height;
	}

	



	public int getWidth() {
		return width;
	}

	



	public void setWidth(int width) {
		this.width = width;
	}

	



	public int getX() {
		return x;
	}

	



	public void setX(int x) {
		this.x = x;
	}

	



	public int getY() {
		return y;
	}

	



	public void setY(int y) {
		this.y = y;
	}

}
