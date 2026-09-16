package dev.hk256.retimer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

/**
 * 应用级配置。
 *
 * 这里唯一要做的事是把 Coil 配成单例并注册视频帧解码器：这样视频缩略图不需要我们
 * 自己抽帧，`AsyncImage` 传视频 Uri 就能直接显示首帧，图片和视频走同一条加载路径。
 */
class MediaTimeFixerApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            // 缩略图列表滚动时淡入，避免图片"跳"出来。
            .crossfade(true)
            .build()
}
