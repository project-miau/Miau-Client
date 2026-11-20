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

import org.lwjgl.Sys;












public class Timer {

	
	private static long resolution = Sys.getTimerResolution();

	
	private static final int QUERY_INTERVAL = 50; 
	private static int queryCount;

	
	private static long currentTime;

	
	private long startTime;

	
	private long lastTime;

	
	private boolean paused;

	static {
		tick();
	}

	


	public Timer() {
		reset();
		resume();
	}

	


	public float getTime() {
		if (!paused) {
			lastTime = currentTime - startTime;
		}

		return (float) ((double) lastTime / (double) resolution);
	}
	


	public boolean isPaused() {
		return paused;
	}

	





	public void pause() {
		paused = true;
	}

	



	public void reset() {
		set(0.0f);
	}

	



	public void resume() {
		paused = false;
		startTime = currentTime - lastTime;
	}

	



	public void set(float newTime) {
		long newTimeInTicks = (long) ((double) newTime * (double) resolution);
		startTime = currentTime - newTimeInTicks;
		lastTime = newTimeInTicks;
	}

	




	public static void tick() {
		currentTime = Sys.getTime();

		
		queryCount ++;
		if (queryCount > QUERY_INTERVAL) {
			queryCount = 0;
			resolution = Sys.getTimerResolution();
		}
	}

	


	public String toString() {
		return "Timer[Time=" + getTime() + ", Paused=" + paused + "]";
	}
}