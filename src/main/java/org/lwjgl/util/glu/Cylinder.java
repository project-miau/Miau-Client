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








public class Cylinder extends Quadric {

	


	public Cylinder() {
		super();
	}

	
























	public void draw(float baseRadius, float topRadius, float height, int slices, int stacks) {

		float da, r, dr, dz;
		float x, y, z, nz, nsign;
		int i, j;

		if (super.orientation == GLU.GLU_INSIDE) {
			nsign = -1.0f;
		} else {
			nsign = 1.0f;
		}

		da = 2.0f * GLU.PI / slices;
		dr = (topRadius - baseRadius) / stacks;
		dz = height / stacks;
		nz = (baseRadius - topRadius) / height;
		

		if (super.drawStyle == GLU.GLU_POINT) {
			glBegin(GL_POINTS);
			for (i = 0; i < slices; i++) {
				x = cos((i * da));
				y = sin((i * da));
				normal3f(x * nsign, y * nsign, nz * nsign);

				z = 0.0f;
				r = baseRadius;
				for (j = 0; j <= stacks; j++) {
					glVertex3f((x * r), (y * r), z);
					z += dz;
					r += dr;
				}
			}
			glEnd();
		} else if (super.drawStyle == GLU.GLU_LINE || super.drawStyle == GLU.GLU_SILHOUETTE) {
			
			if (super.drawStyle == GLU.GLU_LINE) {
				z = 0.0f;
				r = baseRadius;
				for (j = 0; j <= stacks; j++) {
					glBegin(GL_LINE_LOOP);
					for (i = 0; i < slices; i++) {
						x = cos((i * da));
						y = sin((i * da));
						normal3f(x * nsign, y * nsign, nz * nsign);
						glVertex3f((x * r), (y * r), z);
					}
					glEnd();
					z += dz;
					r += dr;
				}
			} else {
				
				if (baseRadius != 0.0) {
					glBegin(GL_LINE_LOOP);
					for (i = 0; i < slices; i++) {
						x = cos((i * da));
						y = sin((i * da));
						normal3f(x * nsign, y * nsign, nz * nsign);
						glVertex3f((x * baseRadius), (y * baseRadius), 0.0f);
					}
					glEnd();
					glBegin(GL_LINE_LOOP);
					for (i = 0; i < slices; i++) {
						x = cos((i * da));
						y = sin((i * da));
						normal3f(x * nsign, y * nsign, nz * nsign);
						glVertex3f((x * topRadius), (y * topRadius), height);
					}
					glEnd();
				}
			}
			
			glBegin(GL_LINES);
			for (i = 0; i < slices; i++) {
				x = cos((i * da));
				y = sin((i * da));
				normal3f(x * nsign, y * nsign, nz * nsign);
				glVertex3f((x * baseRadius), (y * baseRadius), 0.0f);
				glVertex3f((x * topRadius), (y * topRadius), (height));
			}
			glEnd();
		} else if (super.drawStyle == GLU.GLU_FILL) {
			float ds = 1.0f / slices;
			float dt = 1.0f / stacks;
			float t = 0.0f;
			z = 0.0f;
			r = baseRadius;
			for (j = 0; j < stacks; j++) {
				float s = 0.0f;
				glBegin(GL_QUAD_STRIP);
				for (i = 0; i <= slices; i++) {
					if (i == slices) {
						x = sin(0.0f);
						y = cos(0.0f);
					} else {
						x = sin((i * da));
						y = cos((i * da));
					}
					if (nsign == 1.0f) {
						normal3f((x * nsign), (y * nsign), (nz * nsign));
						TXTR_COORD(s, t);
						glVertex3f((x * r), (y * r), z);
						normal3f((x * nsign), (y * nsign), (nz * nsign));
						TXTR_COORD(s, t + dt);
						glVertex3f((x * (r + dr)), (y * (r + dr)), (z + dz));
					} else {
						normal3f(x * nsign, y * nsign, nz * nsign);
						TXTR_COORD(s, t);
						glVertex3f((x * r), (y * r), z);
						normal3f(x * nsign, y * nsign, nz * nsign);
						TXTR_COORD(s, t + dt);
						glVertex3f((x * (r + dr)), (y * (r + dr)), (z + dz));
					}
					s += ds;
				} 
				glEnd();
				r += dr;
				t += dt;
				z += dz;
			} 
		}
	}
}
