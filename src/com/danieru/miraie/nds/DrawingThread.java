package com.danieru.miraie.nds;

/*
Copyright (C) 2012 Jeffrey Quesnelle

This file is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

This file is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with the this software.  If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.danieru.miraie.nds.EmulateActivity.NDSView;

import android.graphics.Canvas;

class DrawingThread extends Thread{
	
	public DrawingThread(EmulatorThread coreThread, NDSView view) {
		super("DrawingThread");
		this.view = view;
		this.coreThread = coreThread;
	}
	
	final NDSView view;
	final EmulatorThread coreThread;
	
	Lock drawEventLock = new ReentrantLock();
	Condition drawEvent = drawEventLock.newCondition();
	
	AtomicBoolean keepDrawing = new AtomicBoolean(true);
	
	static final boolean DO_DIRECT_DRAW = false;
	
	@Override
	public void run() {
		
		while(keepDrawing.get()) {
		
			drawEventLock.lock();
			try {
				drawEvent.await();
			} catch (InterruptedException e) {
			}
			
			if(!keepDrawing.get())
				return;
			
			Canvas canvas = DO_DIRECT_DRAW ? null : view.surfaceHolder.lockCanvas();
			try {
				synchronized(view.surfaceHolder) {
					
					if(canvas != null) {
						if(!DeSmuME.inited)
							continue;
						
						if(view.doForceResize)
							view.resize(view.width, view.height, view.pixelFormat);
						
						if(view.emuBitmapMain == null)
							continue;
	
						if(view.vsync) {
							coreThread.inFrameLock.lock();
								DeSmuME.copyMasterBuffer();
							coreThread.inFrameLock.unlock();
						}
						else {
							DeSmuME.copyMasterBuffer();
						}
						
						if(DO_DIRECT_DRAW)
							DeSmuME.drawToSurface(view.surfaceHolder.getSurface());
						else
							DeSmuME.draw(view.emuBitmapMain, view.emuBitmapTouch,
									view.landscape && view.dontRotate && !view.landscapeStackScreens);
						
						
						if(!DO_DIRECT_DRAW) {

							if(view.lcdSwap) {
								canvas.drawBitmap(view.emuBitmapTouch, view.srcMain, view.destMain, null);
								canvas.drawBitmap(view.emuBitmapMain, view.srcTouch, view.destTouch, null);
							}
							else {
								canvas.drawBitmap(view.emuBitmapMain, view.srcMain, view.destMain, null);
								canvas.drawBitmap(view.emuBitmapTouch, view.srcTouch, view.destTouch, null);
							}
							
							EmulateActivity.controls.drawControls(canvas);
						}
					}

				}
			}
			finally {
				if(!DO_DIRECT_DRAW && canvas != null)
					view.surfaceHolder.unlockCanvasAndPost(canvas);
				drawEventLock.unlock();
			}
		
		}
		
	}
	
}