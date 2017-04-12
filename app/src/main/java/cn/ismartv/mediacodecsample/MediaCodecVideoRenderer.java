package cn.ismartv.mediacodecsample;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;

/**
 * Created by huibin on 4/11/17.
 */

public class MediaCodecVideoRenderer implements Runnable {
    private static final String TAG = "MediaCodecVideoRenderer";

    private String mPath;
    private Surface mSurface;

    public MediaCodecVideoRenderer(String path, Surface surface) {
        mPath = path;
        mSurface = surface;
    }

    @Override
    public void run() {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(mPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int videoIndex = -1;
        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoIndex = i;
                break;
            }
        }

        mediaExtractor.selectTrack(videoIndex);
        MediaFormat mediaFormat = mediaExtractor.getTrackFormat(videoIndex);
        String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec videoCodec = null;
        try {
            videoCodec = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            e.printStackTrace();
        }

        videoCodec.configure(mediaFormat, mSurface, null, 0);

        videoCodec.start();

        ByteBuffer[] inputBuffers = videoCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = videoCodec.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean isEOS = false;
        long startMs = System.currentTimeMillis();

        while (!Thread.interrupted()) {
            if (!isEOS) {
                int inIndex = videoCodec.dequeueInputBuffer(0);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    int sampleSize = mediaExtractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        // We shouldn't stop the playback at this point, just pass the EOS
                        // flag to decoder, we will get it again from the
                        // dequeueOutputBuffer
                        Log.d("DecodeActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        videoCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        videoCodec.queueInputBuffer(inIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                        mediaExtractor.advance();
                    }
                }
            }

            int outIndex = videoCodec.dequeueOutputBuffer(info, 0);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                    outputBuffers = videoCodec.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d("DecodeActivity", "New format " + videoCodec.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                    break;
                default:
                    ByteBuffer buffer = outputBuffers[outIndex];
                    Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);
                    Log.d(TAG, "default index: " + outIndex);
                    // We use a very simple clock to keep the video FPS, or the video
                    // playback will be too fast
                    while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                        try {
                            sleep(1);
                            Log.d(TAG, " sleep(10);");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    videoCodec.releaseOutputBuffer(outIndex, true);
                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }
        videoCodec.stop();
        videoCodec.release();
        mediaExtractor.release();
    }
}
