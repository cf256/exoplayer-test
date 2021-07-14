package no.rikstv.exoplayertest;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.Util;
import com.google.common.primitives.Ints;
import org.checkerframework.checker.nullness.compatqual.NullableType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


class CustomTrackSelector extends DefaultTrackSelector {
    private static final int[] NO_TRACKS = new int[0];
    private static final float FRACTION_TO_CONSIDER_FULLSCREEN = 0.98f;


    public CustomTrackSelector(Context context) {
        super(context, new AdaptiveTrackSelection.Factory());
    }

    @Nullable
    @Override
    protected ExoTrackSelection.Definition selectVideoTrack(TrackGroupArray groups, int[][] formatSupport, int mixedMimeTypeAdaptationSupports, Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
        ExoTrackSelection.Definition definition = null;
        if (!params.forceHighestSupportedBitrate
                && !params.forceLowestBitrate
                && enableAdaptiveTrackSelection) {
            definition =
                    selectAdaptiveVideoTrack(groups, formatSupport, mixedMimeTypeAdaptationSupports, params);
        }
        if (definition == null) {
            definition = selectFixedVideoTrack(groups, formatSupport, params);
        }
        return definition;
    }

    @Nullable
    private static ExoTrackSelection.Definition selectAdaptiveVideoTrack(
            TrackGroupArray groups,
            @RendererCapabilities.Capabilities int[][] formatSupport,
            @RendererCapabilities.AdaptiveSupport int mixedMimeTypeAdaptationSupports,
            Parameters params) {
        int requiredAdaptiveSupport =
                params.allowVideoNonSeamlessAdaptiveness
                        ? (RendererCapabilities.ADAPTIVE_NOT_SEAMLESS | RendererCapabilities.ADAPTIVE_SEAMLESS)
                        : RendererCapabilities.ADAPTIVE_SEAMLESS;
        boolean allowMixedMimeTypes =
                params.allowVideoMixedMimeTypeAdaptiveness
                        && (mixedMimeTypeAdaptationSupports & requiredAdaptiveSupport) != 0;
        for (int i = 0; i < groups.length; i++) {
            TrackGroup group = groups.get(i);
            int[] adaptiveTracks =
                    getAdaptiveVideoTracksForGroup(
                            group,
                            formatSupport[i],
                            allowMixedMimeTypes,
                            requiredAdaptiveSupport,
                            params.maxVideoWidth,
                            params.maxVideoHeight,
                            params.maxVideoFrameRate,
                            params.maxVideoBitrate,
                            params.minVideoWidth,
                            params.minVideoHeight,
                            params.minVideoFrameRate,
                            params.minVideoBitrate,
                            params.viewportWidth,
                            params.viewportHeight,
                            params.viewportOrientationMayChange);
            if (adaptiveTracks.length > 0) {
                return new ExoTrackSelection.Definition(group, adaptiveTracks);
            }
        }
        return null;
    }

    private static int[] getAdaptiveVideoTracksForGroup(
            TrackGroup group,
            @RendererCapabilities.Capabilities int[] formatSupport,
            boolean allowMixedMimeTypes,
            int requiredAdaptiveSupport,
            int maxVideoWidth,
            int maxVideoHeight,
            int maxVideoFrameRate,
            int maxVideoBitrate,
            int minVideoWidth,
            int minVideoHeight,
            int minVideoFrameRate,
            int minVideoBitrate,
            int viewportWidth,
            int viewportHeight,
            boolean viewportOrientationMayChange) {
        if (group.length < 2) {
            return NO_TRACKS;
        }

        List<Integer> selectedTrackIndices =
                getViewportFilteredTrackIndices(
                        group, viewportWidth, viewportHeight, viewportOrientationMayChange);
        if (selectedTrackIndices.size() < 2) {
            return NO_TRACKS;
        }

        String selectedMimeType = null;
        if (!allowMixedMimeTypes) {
            // Select the mime type for which we have the most adaptive tracks.
            HashSet<@NullableType String> seenMimeTypes = new HashSet<>();
            int selectedMimeTypeTrackCount = 0;
            for (int i = 0; i < selectedTrackIndices.size(); i++) {
                int trackIndex = selectedTrackIndices.get(i);
                String sampleMimeType = group.getFormat(trackIndex).sampleMimeType;
                if (seenMimeTypes.add(sampleMimeType)) {
                    int countForMimeType =
                            getAdaptiveVideoTrackCountForMimeType(
                                    group,
                                    formatSupport,
                                    requiredAdaptiveSupport,
                                    sampleMimeType,
                                    maxVideoWidth,
                                    maxVideoHeight,
                                    maxVideoFrameRate,
                                    maxVideoBitrate,
                                    minVideoWidth,
                                    minVideoHeight,
                                    minVideoFrameRate,
                                    minVideoBitrate,
                                    selectedTrackIndices);
                    if (countForMimeType > selectedMimeTypeTrackCount) {
                        selectedMimeType = sampleMimeType;
                        selectedMimeTypeTrackCount = countForMimeType;
                    }
                }
            }
        }

        // Filter by the selected mime type.
        filterAdaptiveVideoTrackCountForMimeType(
                group,
                formatSupport,
                requiredAdaptiveSupport,
                selectedMimeType,
                maxVideoWidth,
                maxVideoHeight,
                maxVideoFrameRate,
                maxVideoBitrate,
                minVideoWidth,
                minVideoHeight,
                minVideoFrameRate,
                minVideoBitrate,
                selectedTrackIndices);

        return selectedTrackIndices.size() < 2 ? NO_TRACKS : Ints.toArray(selectedTrackIndices);
    }

    private static void filterAdaptiveVideoTrackCountForMimeType(
            TrackGroup group,
            @RendererCapabilities.Capabilities int[] formatSupport,
            int requiredAdaptiveSupport,
            @Nullable String mimeType,
            int maxVideoWidth,
            int maxVideoHeight,
            int maxVideoFrameRate,
            int maxVideoBitrate,
            int minVideoWidth,
            int minVideoHeight,
            int minVideoFrameRate,
            int minVideoBitrate,
            List<Integer> selectedTrackIndices) {
        for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
            int trackIndex = selectedTrackIndices.get(i);
            if (!isSupportedAdaptiveVideoTrack(
                    group.getFormat(trackIndex),
                    mimeType,
                    formatSupport[trackIndex],
                    requiredAdaptiveSupport,
                    maxVideoWidth,
                    maxVideoHeight,
                    maxVideoFrameRate,
                    maxVideoBitrate,
                    minVideoWidth,
                    minVideoHeight,
                    minVideoFrameRate,
                    minVideoBitrate)) {
                selectedTrackIndices.remove(i);
            }
        }
    }

    private static boolean isSupportedAdaptiveVideoTrack(
            Format format,
            @Nullable String mimeType,
            @RendererCapabilities.Capabilities int formatSupport,
            int requiredAdaptiveSupport,
            int maxVideoWidth,
            int maxVideoHeight,
            int maxVideoFrameRate,
            int maxVideoBitrate,
            int minVideoWidth,
            int minVideoHeight,
            int minVideoFrameRate,
            int minVideoBitrate) {
        if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
            // Ignore trick-play tracks for now.
            return false;
        }
        boolean allowExceedsCapabilities = false;

        if (Build.MODEL.equals("SRT412")) {
            allowExceedsCapabilities = true;
        }
        return isSupported(formatSupport, /* allowExceedsCapabilities= */ allowExceedsCapabilities)
                && ((formatSupport & requiredAdaptiveSupport) != 0)
                && (mimeType == null || Util.areEqual(format.sampleMimeType, mimeType))
                && (format.width == Format.NO_VALUE
                || (minVideoWidth <= format.width && format.width <= maxVideoWidth))
                && (format.height == Format.NO_VALUE
                || (minVideoHeight <= format.height && format.height <= maxVideoHeight))
                && (format.frameRate == Format.NO_VALUE
                || (minVideoFrameRate <= format.frameRate && format.frameRate <= maxVideoFrameRate))
                && (format.bitrate == Format.NO_VALUE
                || (minVideoBitrate <= format.bitrate && format.bitrate <= maxVideoBitrate));
    }

    private static List<Integer> getViewportFilteredTrackIndices(
            TrackGroup group, int viewportWidth, int viewportHeight, boolean orientationMayChange) {
        // Initially include all indices.
        ArrayList<Integer> selectedTrackIndices = new ArrayList<>(group.length);
        for (int i = 0; i < group.length; i++) {
            selectedTrackIndices.add(i);
        }

        if (viewportWidth == Integer.MAX_VALUE || viewportHeight == Integer.MAX_VALUE) {
            // Viewport dimensions not set. Return the full set of indices.
            return selectedTrackIndices;
        }

        int maxVideoPixelsToRetain = Integer.MAX_VALUE;
        for (int i = 0; i < group.length; i++) {
            Format format = group.getFormat(i);
            // Keep track of the number of pixels of the selected format whose resolution is the
            // smallest to exceed the maximum size at which it can be displayed within the viewport.
            // We'll discard formats of higher resolution.
            if (format.width > 0 && format.height > 0) {
                Point maxVideoSizeInViewport =
                        getMaxVideoSizeInViewport(
                                orientationMayChange, viewportWidth, viewportHeight, format.width, format.height);
                int videoPixels = format.width * format.height;
                if (format.width >= (int) (maxVideoSizeInViewport.x * FRACTION_TO_CONSIDER_FULLSCREEN)
                        && format.height >= (int) (maxVideoSizeInViewport.y * FRACTION_TO_CONSIDER_FULLSCREEN)
                        && videoPixels < maxVideoPixelsToRetain) {
                    maxVideoPixelsToRetain = videoPixels;
                }
            }
        }

        // Filter out formats that exceed maxVideoPixelsToRetain. These formats have an unnecessarily
        // high resolution given the size at which the video will be displayed within the viewport. Also
        // filter out formats with unknown dimensions, since we have some whose dimensions are known.
        if (maxVideoPixelsToRetain != Integer.MAX_VALUE) {
            for (int i = selectedTrackIndices.size() - 1; i >= 0; i--) {
                Format format = group.getFormat(selectedTrackIndices.get(i));
                int pixelCount = format.getPixelCount();
                if (pixelCount == Format.NO_VALUE || pixelCount > maxVideoPixelsToRetain) {
                    selectedTrackIndices.remove(i);
                }
            }
        }

        return selectedTrackIndices;
    }

    /**
     * Given viewport dimensions and video dimensions, computes the maximum size of the video as it
     * will be rendered to fit inside of the viewport.
     */
    private static Point getMaxVideoSizeInViewport(
            boolean orientationMayChange,
            int viewportWidth,
            int viewportHeight,
            int videoWidth,
            int videoHeight) {
        if (orientationMayChange && (videoWidth > videoHeight) != (viewportWidth > viewportHeight)) {
            // Rotation is allowed, and the video will be larger in the rotated viewport.
            int tempViewportWidth = viewportWidth;
            viewportWidth = viewportHeight;
            viewportHeight = tempViewportWidth;
        }

        if (videoWidth * viewportHeight >= videoHeight * viewportWidth) {
            // Horizontal letter-boxing along top and bottom.
            return new Point(viewportWidth, Util.ceilDivide(viewportWidth * videoHeight, videoWidth));
        } else {
            // Vertical letter-boxing along edges.
            return new Point(Util.ceilDivide(viewportHeight * videoWidth, videoHeight), viewportHeight);
        }
    }

    private static int getAdaptiveVideoTrackCountForMimeType(
            TrackGroup group,
            @RendererCapabilities.Capabilities int[] formatSupport,
            int requiredAdaptiveSupport,
            @Nullable String mimeType,
            int maxVideoWidth,
            int maxVideoHeight,
            int maxVideoFrameRate,
            int maxVideoBitrate,
            int minVideoWidth,
            int minVideoHeight,
            int minVideoFrameRate,
            int minVideoBitrate,
            List<Integer> selectedTrackIndices) {
        int adaptiveTrackCount = 0;
        for (int i = 0; i < selectedTrackIndices.size(); i++) {
            int trackIndex = selectedTrackIndices.get(i);
            if (isSupportedAdaptiveVideoTrack(
                    group.getFormat(trackIndex),
                    mimeType,
                    formatSupport[trackIndex],
                    requiredAdaptiveSupport,
                    maxVideoWidth,
                    maxVideoHeight,
                    maxVideoFrameRate,
                    maxVideoBitrate,
                    minVideoWidth,
                    minVideoHeight,
                    minVideoFrameRate,
                    minVideoBitrate)) {
                adaptiveTrackCount++;
            }
        }
        return adaptiveTrackCount;
    }

    @Nullable
    private static ExoTrackSelection.Definition selectFixedVideoTrack(
            TrackGroupArray groups, @RendererCapabilities.Capabilities int[][] formatSupport, Parameters params) {
        int selectedTrackIndex = C.INDEX_UNSET;
        @Nullable TrackGroup selectedGroup = null;
        @Nullable VideoTrackScore selectedTrackScore = null;
        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            TrackGroup trackGroup = groups.get(groupIndex);
            List<Integer> viewportFilteredTrackIndices =
                    getViewportFilteredTrackIndices(
                            trackGroup,
                            params.viewportWidth,
                            params.viewportHeight,
                            params.viewportOrientationMayChange);
            @RendererCapabilities.Capabilities int[] trackFormatSupport = formatSupport[groupIndex];
            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                Format format = trackGroup.getFormat(trackIndex);
                if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0) {
                    // Ignore trick-play tracks for now.
                    continue;
                }
                if (isSupported(
                        trackFormatSupport[trackIndex], params.exceedRendererCapabilitiesIfNecessary)) {
                    VideoTrackScore trackScore =
                            new VideoTrackScore(
                                    format,
                                    params,
                                    trackFormatSupport[trackIndex],
                                    viewportFilteredTrackIndices.contains(trackIndex));
                    if (!trackScore.isWithinMaxConstraints && !params.exceedVideoConstraintsIfNecessary) {
                        // Track should not be selected.
                        continue;
                    }
                    if (selectedTrackScore == null || trackScore.compareTo(selectedTrackScore) > 0) {
                        selectedGroup = trackGroup;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                    }
                }
            }
        }

        return selectedGroup == null
                ? null
                : new ExoTrackSelection.Definition(selectedGroup, selectedTrackIndex);
    }
}
