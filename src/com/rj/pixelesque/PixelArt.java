package com.rj.pixelesque;

import java.io.File;

import processing.core.PApplet;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;

import com.rj.processing.mt.Cursor;
import com.rj.processing.mt.MTManager;
import com.rj.processing.mt.Point;
import com.rj.processing.mt.TouchListener;

import de.devmil.common.ui.color.ColorSelectorActivity;

public class PixelArt extends PApplet implements TouchListener {
	private final static boolean DEBUG = false;

	
	
	public final static int EXPORT_SMALL_LONGSIDE = 320;
	public final static int EXPORT_MEDIUM_LONGSIDE = 640;
	public final static int EXPORT_LARGE_LONGSIDE = 1080;

	public final static int SHARE_MEDIUM_LONGSIDE = 500;

	
	public MTManager mtManager;
	volatile PixelData art;
	private ScaleGestureDetector mScaleDetector;
	private PixelArtState state;
	
	
	public static boolean isHorizontal() {
		return Build.VERSION.SDK_INT > 10;
	}
	
	public static final int LOAD_ACTIVITY = 313;
	public static final int COLOR_ACTIVITY = 315;
	
	
	public int sketchWidth() { return this.screenWidth; }
	public int sketchHeight() { return this.screenHeight; }
	public String sketchRenderer() { return PApplet.OPENGL; }
	public boolean keepTitlebar() { return isHorizontal(); }
	public boolean keepStatusbar() { return true; }
	
	
	RelativeLayout bbbar;
	PixelArtStateView buttonbar;
	
	@Override
	public void onCreate(final Bundle savedinstance) {
		super.onCreate(savedinstance);
		figureOutOrientation();
		 
		bbbar = (RelativeLayout)getLayoutInflater().inflate(com.rj.pixelesque.R.layout.buttonbar, null);
		this.setContentView(bbbar, new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		buttonbar = (PixelArtStateView)bbbar.findViewById(com.rj.pixelesque.R.id.buttonbarz);
		ViewGroup g = (ViewGroup)bbbar.findViewById(com.rj.pixelesque.R.id.surfaceholder);
		g.addView(surfaceView);
	}
	
	public void figureOutOrientation() {
		if (isHorizontal()) {
			setRequestedOrientation(6 /*ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE*/);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}
	
	
	
	@Override
	public void setup() {
		hint(DISABLE_DEPTH_TEST);
		hint(DISABLE_OPENGL_ERROR_REPORT);
		hint(PApplet.DISABLE_ACCURATE_TEXTURES);
		hint(PApplet.DISABLE_DEPTH_MASK);
		hint(PApplet.DISABLE_DEPTH_SORT);
	    frameRate(60);
	    	    
	    debug();
	    
	    
	    mtManager = new MTManager();
	    mtManager.addTouchListener(this);

	    mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());
	    
	    actualsetup();
	}
		
	
	public void debug() {
		  // Place this inside your setup() method
		  final DisplayMetrics dm = new DisplayMetrics();
		  getWindowManager().getDefaultDisplay().getMetrics(dm);
		  final float density = dm.density; 
		  final int densityDpi = dm.densityDpi;
		  println("density is " + density); 
		  println("densityDpi is " + densityDpi);
		  println("HEY! the screen size is "+width+"x"+height);
	}
	
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		float avgx;
		float avgy;
		
	    @Override
	    public boolean onScale(ScaleGestureDetector detector) {
	    	if (state.mode == PixelArtState.DRAW  || state.mode == PixelArtState.ERASER) return false;
	    	
	    	float[] coords = art.getDataCoordsFloatFromXY(PixelArt.this, detector.getFocusX(), detector.getFocusY());

	    	
	        art.scale *= detector.getScaleFactor();
	        // Don't let the object get too small or too large.
	        art.scale = Math.max(1f, Math.min(art.scale, 5.0f));
	        
	        if (art.scale < 1.1f && art.scale > 0.9f) {
	        	art.scale = 1f;
	        }
	        
	        float[] postcoords = art.getXYFromDataCoordsFloat(PixelArt.this, coords[0], coords[1]);
	        float diffx = detector.getFocusX() -  postcoords[0];
	        float diffy = detector.getFocusY() -  postcoords[1];
	        avgx = avgx / 2 + diffx / 2;
	        avgy = avgy / 2 + diffy / 2;
	        //Log.d("Pixelesque", "SCALE: moving: "+diffx+", "+diffy  + "   orig:"+coords[0]+","+coords[1]+ "     post: "+postcoords[0]+","+postcoords[1]);
	        moveArt(avgx, avgy);
	        

	        
	        return true;
	    }
	    
	    @Override
	    public boolean onScaleBegin(ScaleGestureDetector detector) {
	    	avgx = 0;
	    	avgy = 0;
	    	// TODO Auto-generated method stub
	    	return super.onScaleBegin(detector);
	    }
	}


	
	
	@Override
	public boolean surfaceTouchEvent(final MotionEvent event) {
		if (mtManager != null) mtManager.surfaceTouchEvent(event);
	    if (mScaleDetector != null) mScaleDetector.onTouchEvent(event);
	    //touchEvent(event);
	    return super.surfaceTouchEvent(event);
	}
	
	Cursor movingCursor;
	
	@Override
	public void touchAllUp(Cursor c) {
		if (art == null) return;
		art.clearCursors();
	}
	@Override
	public void touchDown(Cursor c) {
		if (art == null) return;
		if (DEBUG) Log.d("Pixelesque", "DOWN "+c);
		if (mScaleDetector != null && !mScaleDetector.isInProgress() && art.isValid(art.getDataCoordsFromXY(this, c.firstPoint.x, c.firstPoint.y))) {
			if (DEBUG) Log.d("Pixelesque", "DOWNADDED"+c);
			art.addCursor(c);
		}
		if (art.cursors.size() <= 1) {
			movingCursor = c;
		}
		if (state.mode == PixelArtState.DRAW || state.mode == PixelArtState.ERASER) {
			int[] last = art.getDataCoordsFromXY(this, c.currentPoint.x, c.currentPoint.y);
			int x = last[0];
			int y = last[1];
			if (state.mode == PixelArtState.DRAW) art.setColor(x,y,state.selectedColor);
			if (state.mode == PixelArtState.ERASER) art.eraseColor(x, y);

		}		

	}
	
	@Override
	public void touchMoved(Cursor c) {
		if (art == null) return;
		int[] coords1 = art.getDataCoordsFromXY(this, c.firstPoint.x, c.firstPoint.y);
		int[] coords2 = art.getDataCoordsFromXY(this, c.currentPoint.x, c.currentPoint.y);
		if (DEBUG) Log.d("Pixelesque", "MOV: c1:("+coords1[0]+","+coords1[1]+")  c2:("+coords2[0]+","+coords2[1]+")   movcur:"+movingCursor);

		if (state.mode == PixelArtState.PENCIL) {
			if (art.hasCursor(c)) {
				if (coords1[0] == coords2[0] && coords1[1] == coords2[1] && !mScaleDetector.isInProgress()) {
					
				} else {
					if (DEBUG) Log.d("Pixelesque", "MOV REMOVE: c1:("+coords1[0]+","+coords1[1]+")  c2:("+coords2[0]+","+coords2[1]+")   movcur:"+movingCursor);
					art.removeCursor(c);
				}
			} else if (movingCursor != null && movingCursor.curId == c.curId && !mScaleDetector.isInProgress()) {
				if (c.points.size() > 1) {
					Point p1 = c.points.get(c.points.size()-1);
					Point p2 = c.points.get(c.points.size()-2);
					moveArt(p1.x - p2.x, p1.y - p2.y);
				}
			}
		} else if (state.mode == PixelArtState.DRAW  || state.mode == PixelArtState.ERASER) {
			if (c.points.size() > 2) {
				int[] sectolast = art.getDataCoordsFromXY(this, c.points.get(c.points.size()-2).x, c.points.get(c.points.size()-2).y);
				int[] last = art.getDataCoordsFromXY(this, c.currentPoint.x, c.currentPoint.y);
				int x = last[0];
				int y = last[1];
				if (last[0] != sectolast[0] || last[1] != sectolast[1]) {
					if (state.mode == PixelArtState.DRAW) art.setColor(x,y,state.selectedColor);
					if (state.mode == PixelArtState.ERASER) art.eraseColor(x, y);
				}
			} else {
//				int x = coords1[0];
//				int y = coords1[1];
//				art.flipColor(x, y, state.selectedColor);
			}
		}		
	}
	
	@Override
	public void touchUp(Cursor c) {
		if (art == null) return;
		int[] coords1 = art.getDataCoordsFromXY(this, c.firstPoint.x, c.firstPoint.y);
		int[] coords2 = art.getDataCoordsFromXY(this, c.currentPoint.x, c.currentPoint.y);
		if (DEBUG) Log.d("Pixelesque", "UP: c1:("+coords1[0]+","+coords1[1]+")  c2:("+coords2[0]+","+coords2[1]+")");
	
		if (state.mode == PixelArtState.PENCIL) {
//			Log.d("Pixelesque", "UP MODE: c1:("+coords1[0]+","+coords1[1]+")  c2:("+coords2[0]+","+coords2[1]+")");
			if (art.hasCursor(c)) {
//				Log.d("Pixelesque", "UP ALMOST: c1:("+coords1[0]+","+coords1[1]+")  c2:("+coords2[0]+","+coords2[1]+")");
				if (coords1[0] == coords2[0] && coords1[1] == coords2[1]) {
					int x = coords1[0];
					int y = coords1[1];
//					Log.d("Pixelesque", "UP APPLY: c1:("+coords1[0]+","+coords1[1]+")  c2:("+coords2[0]+","+coords2[1]+")");
					if (state.mode == PixelArtState.PENCIL) art.flipColor(x, y, state.selectedColor);
				} else {
				}
				art.removeCursor(c);
			}		
		} else if (state.mode == PixelArtState.DRAW  || state.mode == PixelArtState.ERASER) {
			art.removeCursor(c);
			
			
		}
		
		buttonbar.updateFromState();
	}


	void actualsetup() {
		initialArt();
		state  = new PixelArtState();
	    buttonbar.setState(state, art, this);
	    artChangedName();
	}
	
	void initialArt() {
		Configuration config = getResources().getConfiguration();
		int screen = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
		if (screen <= Configuration.SCREENLAYOUT_SIZE_LARGE) {
			art = new PixelData(12,16);
		} else {
			art = new PixelData(21,12);
		}
		checkBounds();
		final String lastopenedpath = StorageUtils.getLastOpenedFile(this);
		Log.d("PixelArt", "Last opened: "+lastopenedpath);
		if (lastopenedpath != null) {
			runOnUiThread( new Runnable() { public void run() {openArt(lastopenedpath);}});
		}
	}
	
	void setArt(PixelData art) {
		this.art = art;
		checkBounds();
		buttonbar.setState(state, art, this);
	}
	
	void openArt(String art) {
		new OpenArtTask().execute(art);
	}
	
	void newArt(int width, int height) {
		new NewArtTask(width, height).execute();
	}
	
	class OpenArtTask extends AsyncTask<String, Void, PixelData> {
		Dialog d;
		@Override
		protected void onPreExecute() {
			d  = ProgressDialog.show(PixelArt.this, "Loading...", "Just a moment");
			super.onPreExecute();
		}
		@Override
		protected PixelData doInBackground(String... params) {
			String path = params[0];
			try {
				Bitmap image = StorageUtils.loadFile(PixelArt.this, path, PixelArt.this, true);
				if (image == null) return null;
				PixelData art = new PixelData(image, new File(path).getName().replace(".png", ""));
				return art;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}		
		@Override
		protected void onPostExecute(PixelData result) {
			super.onPostExecute(result);
			if (result != null) {
				setArt(result);
				artChangedName();
			} else
				Toast.makeText(PixelArt.this, "There was an error opening the image", Toast.LENGTH_SHORT).show();
			d.dismiss();
		}	
	}

	class NewArtTask extends AsyncTask<Void, Void, PixelData> {
		int width, height;
		public NewArtTask(int width, int height) {
			this.width = width; this.height = height;
		}
		Dialog d;
		@Override
		protected void onPreExecute() {
			d  = ProgressDialog.show(PixelArt.this, "Loading...", "");
			super.onPreExecute();
		}
		@Override
		protected PixelData doInBackground(Void... params) {
			try {
				PixelData art = new PixelData(width, height);
				return art;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}		
		@Override
		protected void onPostExecute(PixelData result) {
			super.onPostExecute(result);
			if (result != null) {
				setArt(result);
				artChangedName();
			} else
				Toast.makeText(PixelArt.this, "There was an error opening the image", Toast.LENGTH_SHORT).show();
			d.dismiss();
		}	
	}
	@Override
	public void draw() {
		background(0);
		PApplet p = this;
		art.draw(p);
	}
	
	public void moveArt(float dx, float dy) {
        art.topx += dx;
        art.topy += dy;
        
        checkBounds();
	}
	
	public void checkBounds() {
        if (art.topx > 0) art.topx = 0;
        if (art.topy > 0) art.topy = 0;
        if (art.topx + art.getWidth(this) < this.width) art.topx = Math.min(0, (int)(width - art.getWidth(this)));
        if (art.topy + art.getHeight(this) < this.height) art.topy = Math.min(0, (int)(height - art.getHeight(this)));
        
        if (art.getWidth(this) < this.width) art.topx = (this.width - art.getWidth(this))/2;
	}


	@Override
	public void mousePressed() {
//		int[] coords = art.getDataCoordsFromXY(this, mouseX, mouseY);
//		int x = coords[0];
//		int y = coords[1];
//		art.flipColor(x, y);
	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	
	public void artChangedName() {
		this.runOnUiThread(new ChangeName());
	}
	public class ChangeName implements Runnable {
		public void run() {
			TextView view = (TextView)findViewById(com.rj.pixelesque.R.id.picturename);
			if (art != null && art.name != null)  {
				if (view != null) view.setText(art.name);
				setTitle("Pixelesque - "+art.name);
			} else {
				if (view != null) view.setText("New Pixel Art");
				setTitle("Pixelesque - "+"New Pixel Art");
			}
			
		}
	}
		
	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(com.rj.pixelesque.R.menu.mainmenu, menu);
	    return true;
	}
	
	
	@Override
	public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
		if (DEBUG) Log.d("PixelArt", "menu z : "+item.getItemId());
		
	    switch (item.getItemId()) {
		    case com.rj.pixelesque.R.id.main_menu_save:
		        save();
		        return true;
		    case com.rj.pixelesque.R.id.main_menu_save_as:
		        saveas();
		        return true;
		    case com.rj.pixelesque.R.id.main_menu_export:
		        export();
		        return true;
		    case com.rj.pixelesque.R.id.main_menu_share:
		        share();
		        return true;
		    case com.rj.pixelesque.R.id.main_menu_open:
		        load();
		        return true;
		    case com.rj.pixelesque.R.id.main_menu_new:
		        shownew();
		        return true;
		    case com.rj.pixelesque.R.id.main_menu_clear:
		        clear();
		        return true;

		    default:
		        return super.onOptionsItemSelected(item);
	    }
	}
	
	public void load() {
		Intent intent = new Intent(this, ArtListActivity.class);
		startActivityForResult(intent, LOAD_ACTIVITY);
	}
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == LOAD_ACTIVITY) {
			if (data == null) return;
			String path = data.getStringExtra(ArtListActivity.PATH);
			if (path != null) openArt(path);
		} else if (requestCode == COLOR_ACTIVITY ) {
			if (data == null) return;
			int color = data.getIntExtra(ColorSelectorActivity.RESULT_COLOR, 0);
			state.selectedColor = color;
			buttonbar.updateFromState();
		}
	}
	
	public void clear() {
		art.rectangle(0, 0, art.width-1, art.height-1, Color.TRANSPARENT);
		buttonbar.updateFromState();
	}
	
	public void shownew() {
		Dialogs.showNewDialog(this);
	}
	
	public void save() {
		if (art.name == null)
			saveas();
		else
			save(art.name);
	}
	public void saveas() {
		Dialogs.showSaveAs(this);
	}
	
	public void save(String name) {
		art.setName(name);
		new SaveTask(name, -1, -1, art, this).execute();
	}
	
	
	public void export() {
		Dialogs.showExport(this);
	}
	
	
	public void export(int longside) {
		export(longside, null);
	}
	public void export(int longside, String extra) {
		if (extra != null) {
			extra = "-"+extra;
		} else {
			extra = "";
		}
		if (art.width > art.height) {
			if (art.name != null) export(art.name+extra, longside, -1);
			else export(null, longside, -1);
		} else {
			if (art.name != null) export(art.name+extra, -1, longside);
			else export(null, -1, longside);
		}
	}
	
	public void export(String name, int width, int height) {
		File exportloc = StorageUtils.getExportDirectory(this);
		new SaveTask(name, width, height, art, this, exportloc, true, false).execute(null, null);
	}
	 
	public void share() {
		share(SHARE_MEDIUM_LONGSIDE);
	}

	public void share(int longside) {
		if (art.width > art.height) {
			if (art.name != null) share(art.name+"shared", longside, -1);
			else share(null, longside, -1);
		} else {
			if (art.name != null) share(art.name+"shared", -1, longside);
			else share(null, -1, longside);
		}
	}
	
	public void share(String name, int width, int height) {
		File exportloc = StorageUtils.getExportDirectory(this);
		new SaveTask(name, width, height, art, this, exportloc, false, true).execute(null, null);
	}
	

}
