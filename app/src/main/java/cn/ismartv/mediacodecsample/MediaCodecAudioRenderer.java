package cn.ismartv.mediacodecsample;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * Created by huibin on 4/11/17.
 */

public class MediaCodecAudioRenderer implements Runnable {
    private static final String TAG = "MediaCodecAudioRenderer";
    private static final int TIMEOUT_US = 1000;
    private boolean eosReceived;

    private String mPath;

    public MediaCodecAudioRenderer(String path) {
        mPath = path;
    }

    @Override
    public void run() {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(mPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int audioIndex = -1;

        int trackCount = mediaExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                audioIndex = i;
                break;
            }
        }

        mediaExtractor.selectTrack(audioIndex);
        MediaFormat audioFormat = mediaExtractor.getTrackFormat(audioIndex);
        MediaCodec audioCodec = null;
        try {
            audioCodec = MediaCodec.createDecoderByType(audioFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
        }
        int sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        //获取当前帧的通道数
        int channel = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        //音频文件长度
//        long duration = format.getLong(MediaFormat.KEY_DURATION);
//        Log.d(TAG, "length:" + duration / 1000000);

        //配置MediaCodec
        audioCodec.configure(audioFormat, null, null, 0);
        audioCodec.start();

        ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int buffsize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        // 创建AudioTrack对象
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffsize,
                AudioTrack.MODE_STREAM);
        //启动AudioTrack
        audioTrack.play();

        while (!eosReceived) {
            int inIndex = audioCodec.dequeueInputBuffer(TIMEOUT_US);
            if (inIndex >= 0) {
                ByteBuffer buffer = inputBuffers[inIndex];
                //从MediaExtractor中读取一帧待解数据
                int sampleSize = mediaExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    // We shouldn't stop the playback at this point, just pass the EOS
                    // flag to audioCodec, we will get it again from the
                    // dequeueOutputBuffer
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    audioCodec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {
                    //向MediaDecoder输入一帧待解码数据
                    audioCodec.queueInputBuffer(inIndex, 0, sampleSize, mediaExtractor.getSampleTime(), 0);
                    mediaExtractor.advance();
                }
                //从MediaDecoder队列取出一帧解码后的数据
                int outIndex = audioCodec.dequeueOutputBuffer(info, TIMEOUT_US);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = audioCodec.getOutputBuffers();
                        break;

                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        MediaFormat format = audioCodec.getOutputFormat();
                        Log.d(TAG, "New format " + format);
                        audioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));

                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d(TAG, "dequeueOutputBuffer timed out!");
                        break;

                    default:
                        Log.d(TAG, "default index: " + outIndex);
                        ByteBuffer outBuffer = outputBuffers[outIndex];
                        //Log.v(TAG, "outBuffer: " + outBuffer);

                        final byte[] chunk = new byte[info.size];
                        // Read the buffer all at once
                        outBuffer.get(chunk);
                        //清空buffer,否则下一次得到的还会得到同样的buffer
                        outBuffer.clear();
                        // AudioTrack write data
                        audioTrack.write(chunk, info.offset, info.offset + info.size);
                        audioCodec.releaseOutputBuffer(outIndex, false);
                        break;
                }

                // 所有帧都解码、播放完之后退出循环
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }
        }

        //释放MediaDecoder资源
        audioCodec.stop();
        audioCodec.release();
        audioCodec = null;

        //释放MediaExtractor资源
        mediaExtractor.release();
        mediaExtractor = null;

        //释放AudioTrack资源
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
    }
}
