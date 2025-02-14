package com.soywiz.korim.format

import com.soywiz.kds.*
import com.soywiz.kds.iterators.*
import com.soywiz.korim.atlas.*
import com.soywiz.korim.bitmap.*

open class ImageData constructor(
    val frames: List<ImageFrame>,
    val loopCount: Int = 0,
    val width: Int = frames.firstOrNull()?.width ?: 1,
    val height: Int = frames.firstOrNull()?.height ?: 1,
    val layers: List<ImageLayer> = fastArrayListOf(),
    val animations: List<ImageAnimation> = fastArrayListOf(),
    val name: String? = null,
) : Extra by Extra.Mixin() {
    companion object {
        operator fun invoke(
            loopCount: Int = 0,
            layers: List<ImageLayer> = fastArrayListOf(),
            animations: List<ImageAnimation> = fastArrayListOf(),
            name: String? = null,
        ): ImageData =
            ImageData(animations.first().frames, loopCount = loopCount, layers = layers, animations = animations, name = name)
    }

    val defaultAnimation = ImageAnimation(frames, ImageAnimation.Direction.FORWARD, "default")
    val animationsByName = animations.associateBy { it.name }
    val area: Int get() = frames.area
    val framesByName = frames.associateBy { it.name }
    val framesSortedByPriority = frames.sortedByDescending {
        if (it.main) {
            Int.MAX_VALUE
        } else {
            it.bitmap.width * it.bitmap.height * (it.bitmap.bpp * it.bitmap.bpp)
        }
    }

    val mainBitmap: Bitmap get() = framesSortedByPriority.firstOrNull()?.bitmap
        ?: throw IllegalArgumentException("No bitmap found")

    override fun toString(): String = "ImageData($frames)"
}

data class ImageDataWithAtlas(val image: ImageData, val atlas: AtlasPacker.Result<ImageFrameLayer>)

//fun ImageData.packInAtlas(): ImageDataWithAtlas {
//    val frameLayers = frames.flatMap { it.layerData }.filter { it.includeInAtlas }
//    val atlasResult = AtlasPacker.pack(frameLayers.map { it to it.slice })
//    val translatedFrames = frames.map {
//        ImageFrame(it.index, it.layerData.map {
//            ImageFrameLayer(it.layer, atlasResult.tryGetEntryByKey(it)!!.slice, it.targetX, it.targetY, it.main, it.includeInAtlas)
//        }, it.time)
//    }
//    return ImageDataWithAtlas(ImageData(
//        frames = translatedFrames,
//        loopCount = loopCount,
//        width = width,
//        height = height,
//        layers = layers,
//        animations = animations.map { ImageAnimation(it.frames.map { translatedFrames[it.index] }, it.direction, it.name) }
//    ), atlasResult)
//}

fun ImageData.packInMutableAtlas(mutableAtlas: MutableAtlas<Unit>): ImageData {
    @Suppress("MoveSuspiciousCallableReferenceIntoParentheses")
    val frameLayers = frames.flatMap { it.layerData }.filter { it.includeInAtlas }.sortedByDescending { it.area }
    frameLayers.fastForEach { frameLayer ->
        frameLayer.slice = mutableAtlas.add(frameLayer.slice, Unit, frameLayer.slice.name).slice
    }
    return this

    //val translatedFrames = frames.map {
    //    ImageFrame(it.index, it.layerData.map {
    //        ImageFrameLayer(it.layer, mutableAtlas.add(it.bitmap32, Unit, it.slice.name).slice, it.targetX, it.targetY, it.main, it.includeInAtlas)
    //    }, it.time)
    //}
    //return ImageData(
    //    frames = translatedFrames,
    //    loopCount = loopCount,
    //    width = width,
    //    height = height,
    //    layers = layers,
    //    animations = animations.map { ImageAnimation(it.frames.map { translatedFrames[it.index] }, it.direction, it.name) }
    //)
}

fun ImageDataContainer.packInMutableAtlas(mutableAtlas: MutableAtlas<Unit>): ImageDataContainer {
    this.imageDatas.fastForEach { it.packInMutableAtlas(mutableAtlas) }
    return this
}
