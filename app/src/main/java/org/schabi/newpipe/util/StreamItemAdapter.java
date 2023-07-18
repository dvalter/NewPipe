package org.schabi.newpipe.util;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.Utils;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.util.Utility;

/**
 * A list adapter for a list of {@link Stream streams}.
 * It currently supports {@link VideoStream}, {@link AudioStream} and {@link SubtitlesStream}.
 *
 * @param <T> the primary stream type's class extending {@link Stream}
 * @param <U> the secondary stream type's class extending {@link Stream}
 */
public class StreamItemAdapter<T extends Stream, U extends Stream> extends BaseAdapter {
    @NonNull
    private final StreamInfoWrapper<T> streamsWrapper;
    @NonNull
    private final SparseArrayCompat<SecondaryStreamHelper<U>> secondaryStreams;

    /**
     * Indicates that at least one of the primary streams is an instance of {@link VideoStream},
     * has no audio ({@link VideoStream#isVideoOnly()} returns true) and has no secondary stream
     * associated with it.
     */
    private final boolean hasAnyVideoOnlyStreamWithNoSecondaryStream;

    public StreamItemAdapter(
            @NonNull final StreamInfoWrapper<T> streamsWrapper,
            @NonNull final SparseArrayCompat<SecondaryStreamHelper<U>> secondaryStreams
    ) {
        this.streamsWrapper = streamsWrapper;
        this.secondaryStreams = secondaryStreams;

        this.hasAnyVideoOnlyStreamWithNoSecondaryStream =
                checkHasAnyVideoOnlyStreamWithNoSecondaryStream();
    }

    public StreamItemAdapter(final StreamInfoWrapper<T> streamsWrapper) {
        this(streamsWrapper, new SparseArrayCompat<>(0));
    }

    public List<T> getAll() {
        return streamsWrapper.getStreamsList();
    }

    public SparseArrayCompat<SecondaryStreamHelper<U>> getAllSecondary() {
        return secondaryStreams;
    }

    @Override
    public int getCount() {
        return streamsWrapper.getStreamsList().size();
    }

    @Override
    public T getItem(final int position) {
        return streamsWrapper.getStreamsList().get(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getDropDownView(final int position,
                                final View convertView,
                                final ViewGroup parent) {
        return getCustomView(position, convertView, parent, true);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        return getCustomView(((Spinner) parent).getSelectedItemPosition(),
                convertView, parent, false);
    }

    @NonNull
    private View getCustomView(final int position,
                               final View view,
                               final ViewGroup parent,
                               final boolean isDropdownItem) {
        final var context = parent.getContext();
        View convertView = view;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.stream_quality_item, parent, false);
        }

        final ImageView woSoundIconView = convertView.findViewById(R.id.wo_sound_icon);
        final TextView formatNameView = convertView.findViewById(R.id.stream_format_name);
        final TextView qualityView = convertView.findViewById(R.id.stream_quality);
        final TextView sizeView = convertView.findViewById(R.id.stream_size);

        final T stream = getItem(position);
        final MediaFormat mediaFormat = streamsWrapper.getFormat(position);

        int woSoundIconVisibility = View.GONE;
        String qualityString;

        if (stream instanceof VideoStream) {
            final VideoStream videoStream = ((VideoStream) stream);
            qualityString = videoStream.getResolution();

            if (hasAnyVideoOnlyStreamWithNoSecondaryStream) {
                if (videoStream.isVideoOnly()) {
                    woSoundIconVisibility = secondaryStreams.get(position) != null
                            // It has a secondary stream associated with it, so check if it's a
                            // dropdown view so it doesn't look out of place (missing margin)
                            // compared to those that don't.
                            ? (isDropdownItem ? View.INVISIBLE : View.GONE)
                            // It doesn't have a secondary stream, icon is visible no matter what.
                            : View.VISIBLE;
                } else if (isDropdownItem) {
                    woSoundIconVisibility = View.INVISIBLE;
                }
            }
        } else if (stream instanceof AudioStream) {
            final AudioStream audioStream = ((AudioStream) stream);
            if (audioStream.getAverageBitrate() > 0) {
                qualityString = audioStream.getAverageBitrate() + "kbps";
            } else if (mediaFormat != null) {
                qualityString = mediaFormat.getName();
            } else {
                qualityString = context.getString(R.string.unknown_quality);
            }
        } else if (stream instanceof SubtitlesStream) {
            qualityString = ((SubtitlesStream) stream).getDisplayLanguageName();
            if (((SubtitlesStream) stream).isAutoGenerated()) {
                qualityString += " (" + context.getString(R.string.caption_auto_generated) + ")";
            }
        } else {
            if (mediaFormat == null) {
                qualityString = context.getString(R.string.unknown_quality);
            } else {
                qualityString = mediaFormat.getSuffix();
            }
        }

        if (streamsWrapper.getSizeInBytes(position) > 0) {
            final var secondary = secondaryStreams.get(position);
            if (secondary != null) {
                final long size = secondary.getSizeInBytes()
                        + streamsWrapper.getSizeInBytes(position);
                sizeView.setText(Utility.formatBytes(size));
            } else {
                sizeView.setText(streamsWrapper.getFormattedSize(position));
            }
            sizeView.setVisibility(View.VISIBLE);
        } else {
            sizeView.setVisibility(View.GONE);
        }

        if (stream instanceof SubtitlesStream) {
            formatNameView.setText(((SubtitlesStream) stream).getLanguageTag());
        } else {
            if (mediaFormat == null) {
                formatNameView.setText(context.getString(R.string.unknown_format));
            } else if (mediaFormat == MediaFormat.WEBMA_OPUS) {
                // noinspection AndroidLintSetTextI18n
                formatNameView.setText("opus");
            } else {
                formatNameView.setText(mediaFormat.getName());
            }
        }

        qualityView.setText(qualityString);
        woSoundIconView.setVisibility(woSoundIconVisibility);

        return convertView;
    }

    /**
     * @return if there are any video-only streams with no secondary stream associated with them.
     * @see #hasAnyVideoOnlyStreamWithNoSecondaryStream
     */
    private boolean checkHasAnyVideoOnlyStreamWithNoSecondaryStream() {
        for (int i = 0; i < streamsWrapper.getStreamsList().size(); i++) {
            final T stream = streamsWrapper.getStreamsList().get(i);
            if (stream instanceof VideoStream) {
                final boolean videoOnly = ((VideoStream) stream).isVideoOnly();
                if (videoOnly && secondaryStreams.get(i) == null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * A wrapper class that includes a way of storing the stream sizes.
     *
     * @param <T> the stream type's class extending {@link Stream}
     */
    public static class StreamInfoWrapper<T extends Stream> implements Serializable {
        private static final StreamInfoWrapper<Stream> EMPTY =
                new StreamInfoWrapper<>(Collections.emptyList(), null);
        private static final int SIZE_UNSET = -2;

        private final List<T> streamsList;
        private final long[] streamSizes;
        private final MediaFormat[] streamFormats;
        private final String unknownSize;

        public StreamInfoWrapper(@NonNull final List<T> streamList,
                                 @Nullable final Context context) {
            this.streamsList = streamList;
            this.streamSizes = new long[streamsList.size()];
            this.unknownSize = context == null
                    ? "--.-" : context.getString(R.string.unknown_content);
            this.streamFormats = new MediaFormat[streamsList.size()];
            resetInfo();
        }

        /**
         * Helper method to fetch the sizes and missing media formats
         * of all the streams in a wrapper.
         *
         * @param <X> the stream type's class extending {@link Stream}
         * @param streamsWrapper the wrapper
         * @return a {@link Single} that returns a boolean indicating if any elements were changed
         */
        @NonNull
        public static <X extends Stream> Single<Boolean> fetchMoreInfoForWrapper(
                final StreamInfoWrapper<X> streamsWrapper) {
            final Callable<Boolean> fetchAndSet = () -> {
                boolean hasChanged = false;
                for (final X stream : streamsWrapper.getStreamsList()) {
                    final boolean changeSize = streamsWrapper.getSizeInBytes(stream) <= SIZE_UNSET;
                    final boolean changeFormat = stream.getFormat() == null;
                    if (!changeSize && !changeFormat) {
                        continue;
                    }
                    final Response response = DownloaderImpl.getInstance()
                            .head(stream.getContent());
                    if (changeSize) {
                        final String contentLength = response.getHeader("Content-Length");
                        if (!isNullOrEmpty(contentLength)) {
                            streamsWrapper.setSize(stream, Long.parseLong(contentLength));
                            hasChanged = true;
                        }
                    }
                    if (changeFormat) {
                        hasChanged = retrieveMediaFormat(stream, streamsWrapper, response)
                                || hasChanged;
                    }
                }
                return hasChanged;
            };

            return Single.fromCallable(fetchAndSet)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorReturnItem(true);
        }

        /**
         * Try to retrieve the {@link MediaFormat} for a stream from the request headers.
         *
         * @param <X>            the stream type to get the {@link MediaFormat} for
         * @param stream         the stream to find the {@link MediaFormat} for
         * @param streamsWrapper the wrapper to store the found {@link MediaFormat} in
         * @param response       the response of the head request for the given stream
         * @return {@code true} if the media format could be retrieved; {@code false} otherwise
         */
        private static <X extends Stream> boolean retrieveMediaFormat(
                @NonNull final X stream,
                @NonNull final StreamInfoWrapper<X> streamsWrapper,
                @NonNull final Response response) {
            return retrieveMediaFormatFromFileTypeHeaders(stream, streamsWrapper, response)
                    || retrieveMediaFormatFromContentDispositionHeader(
                            stream, streamsWrapper, response)
                    || retrieveMediaFormatFromContentTypeHeader(stream, streamsWrapper, response);
        }

        private static <X extends Stream> boolean retrieveMediaFormatFromFileTypeHeaders(
                @NonNull final X stream,
                @NonNull final StreamInfoWrapper<X> streamsWrapper,
                @NonNull final Response response) {
            // try to use additional headers from CDNs or servers,
            // e.g. x-amz-meta-file-type (e.g. for SoundCloud)
            final List<String> keys = response.responseHeaders().keySet().stream()
                    .filter(k -> k.endsWith("file-type")).collect(Collectors.toList());
            if (!keys.isEmpty()) {
                for (final String key : keys) {
                    final String suffix = response.getHeader(key);
                    final MediaFormat format = MediaFormat.getFromSuffix(suffix);
                    if (format != null) {
                        streamsWrapper.setFormat(stream, format);
                        return true;
                    }
                }
            }
            return false;
        }

        private static <X extends Stream> boolean retrieveMediaFormatFromContentDispositionHeader(
                @NonNull final X stream,
                @NonNull final StreamInfoWrapper<X> streamsWrapper,
                @NonNull final Response response) {
            // parse the Content-Disposition header,
            // see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition
            // there can be two filename directives
            String contentDisposition = response.getHeader("Content-Disposition");
            if (contentDisposition != null) {
                try {
                    contentDisposition = Utils.decodeUrlUtf8(contentDisposition);
                    final String[] parts = contentDisposition.split(";");
                    for (String part : parts) {
                        final String fileName;
                        part = part.trim();
                        // extract the filename
                        if (part.startsWith("filename=")) {
                            // remove directive and decode
                            fileName = Utils.decodeUrlUtf8(part.substring(9));
                        } else if (part.startsWith("filename*=")) {
                            // The filename* directive passes the encoding of the filename, too.
                            // The encoding is prepended to the actual filename
                            // and the file name *should* be put into quotes.
                            // There needs to be a character (mostly one of the quotes)
                            // which separates the encoding from the filename
                            part = part.substring(10); // remove directive
                            final String encoding = Parser.matchGroup1(
                                    "([0-9|A-Z|a-z|\\-|_]+)", part);
                            // remove encoding and separating character
                            part = part.substring(encoding.length() + 1);
                            final Charset charset = Charset.forName(encoding);
                            // decode info with the given encoding
                            if (charset == null) {
                                continue;
                            }
                            if (charset.equals(Charset.defaultCharset())) {
                                fileName = part;
                            } else {
                                fileName = new String(part.getBytes(charset),
                                        StandardCharsets.UTF_8);
                            }
                        } else {
                            continue;
                        }

                        // extract the file extension / suffix
                        final String[] p = fileName.split("\\.");
                        String suffix = p[p.length - 1];
                        if (suffix.endsWith("\"") || suffix.endsWith("'")) {
                            // remove trailing quotes if present
                            suffix = suffix.substring(0, suffix.length() - 2);
                        }

                        final MediaFormat format = MediaFormat.getFromSuffix(suffix);
                        if (format != null) {
                            streamsWrapper.setFormat(stream, format);
                            return true;
                        }
                    }
                } catch (final Exception ignored) { }
            }
            return false;
        }

        private static <X extends Stream> boolean retrieveMediaFormatFromContentTypeHeader(
                @NonNull final X stream,
                @NonNull final StreamInfoWrapper<X> streamsWrapper,
                @NonNull final Response response) {
            // try to get the format by content type
            // some mime types are not unique for every format, those are omitted
            final List<MediaFormat> formats = MediaFormat.getAllFromMimeType(
                    response.getHeader("Content-Type"));
            final List<MediaFormat> uniqueFormats = new ArrayList<>(formats.size());
            for (int i = 0; i < formats.size(); i++) {
                final MediaFormat format = formats.get(i);
                if (uniqueFormats.stream().filter(f -> f.id == format.id).count() == 0) {
                    uniqueFormats.add(format);
                }
            }
            if (uniqueFormats.size() == 1) {
                streamsWrapper.setFormat(stream, uniqueFormats.get(0));
                return true;
            }
            return false;
        }

        public void resetInfo() {
            Arrays.fill(streamSizes, SIZE_UNSET);
            for (int i = 0; i < streamsList.size(); i++) {
                streamFormats[i] = streamsList.get(i).getFormat();
            }
        }

        public static <X extends Stream> StreamInfoWrapper<X> empty() {
            //noinspection unchecked
            return (StreamInfoWrapper<X>) EMPTY;
        }

        public List<T> getStreamsList() {
            return streamsList;
        }

        public long getSizeInBytes(final int streamIndex) {
            return streamSizes[streamIndex];
        }

        public long getSizeInBytes(final T stream) {
            return streamSizes[streamsList.indexOf(stream)];
        }

        public String getFormattedSize(final int streamIndex) {
            return formatSize(getSizeInBytes(streamIndex));
        }

        private String formatSize(final long size) {
            if (size > -1) {
                return Utility.formatBytes(size);
            }
            return unknownSize;
        }

        public void setSize(final T stream, final long sizeInBytes) {
            streamSizes[streamsList.indexOf(stream)] = sizeInBytes;
        }

        public MediaFormat getFormat(final int streamIndex) {
            return streamFormats[streamIndex];
        }

        public void setFormat(final T stream, final MediaFormat format) {
            streamFormats[streamsList.indexOf(stream)] = format;
        }
    }
}
