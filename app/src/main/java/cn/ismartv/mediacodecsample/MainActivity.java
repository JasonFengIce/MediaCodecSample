package cn.ismartv.mediacodecsample;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MainActivity";
    private static final String[] VIDEO_FILES = {
            "jellyfish-3-mbps-hd-hevc-10bit.mkv",
            "jellyfish-3-mbps-hd-hevc.mkv",
            "jellyfish-3-mbps-hd-h264.mkv",
            "test.ts",
            "test2.ts",
    };

    private PlayerThread mPlayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        setContentView(sv);
    }

    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mPlayer == null) {
            mPlayer = new PlayerThread(holder.getSurface());
            mPlayer.start();


        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayer != null) {
            mPlayer.interrupt();
        }
    }

    private class PlayerThread extends Thread {
        private Surface surface;
        private File file = new File(Environment.getExternalStorageDirectory(), VIDEO_FILES[4]);

        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            new Thread(new MediaCodecVideoRenderer(file.getAbsolutePath(), surface)).start();
            new Thread(new MediaCodecAudioRenderer(file.getAbsolutePath())).start();
        }
    }

}
