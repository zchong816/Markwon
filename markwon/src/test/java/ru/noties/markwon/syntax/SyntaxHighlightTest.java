package ru.noties.markwon.syntax;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;

import ru.noties.markwon.AbstractMarkwonVisitorImpl;
import ru.noties.markwon.MarkwonConfiguration;
import ru.noties.markwon.MarkwonSpansFactory;
import ru.noties.markwon.MarkwonVisitor;
import ru.noties.markwon.RenderProps;
import ru.noties.markwon.RenderPropsImpl;
import ru.noties.markwon.SpanFactory;
import ru.noties.markwon.SpannableBuilder;
import ru.noties.markwon.core.CorePluginBridge;
import ru.noties.markwon.core.MarkwonTheme;
import ru.noties.markwon.image.AsyncDrawableLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = {
        Build.VERSION_CODES.JELLY_BEAN,
        Build.VERSION_CODES.M,
        Build.VERSION_CODES.O
})
// although it is called SyntaxHighlightTest all it does is check that spans are in the correct order
// and syntax highlight is the primary user of this functionality
public class SyntaxHighlightTest {

    // codeSpan must be before actual highlight spans (true reverse of builder)

    // if we go with path of reversing spans inside SpannableBuilder (which
    // might extend SpannableStringBuilder like https://github.com/noties/Markwon/pull/71)
    // then on M (23) codeSpan will always be _before_ actual highlight and thus
    // no highlight will be present
    // note that bad behaviour is present on M (emulator/device/robolectric)
    // other SDKs are added to validate that they do not fail
    @Test
    public void test() {

        // code span must be first in the list, then should go highlight spans
        class Highlight {
        }

        final Object codeSpan = new Object();

        final SyntaxHighlight highlight = new SyntaxHighlight() {
            @NonNull
            @Override
            public CharSequence highlight(@Nullable String info, @NonNull String code) {
                final SpannableStringBuilder builder = new SpannableStringBuilder(code);
                for (int i = 0, length = code.length(); i < length; i++) {
                    builder.setSpan(new Highlight(), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return builder;
            }
        };

        final MarkwonSpansFactory spansFactory = mock(MarkwonSpansFactory.class);
        when(spansFactory.require(any(FencedCodeBlock.class))).thenReturn(new SpanFactory() {
            @Override
            public Object getSpans(@NonNull MarkwonConfiguration configuration, @NonNull RenderProps props) {
                return codeSpan;
            }
        });

        final MarkwonConfiguration configuration = MarkwonConfiguration.builder()
                .syntaxHighlight(highlight)
                .build(mock(MarkwonTheme.class), mock(AsyncDrawableLoader.class), spansFactory);

        final Map<Class<? extends Node>, MarkwonVisitor.NodeVisitor<? extends Node>> visitorMap = Collections.emptyMap();

        final MarkwonVisitor visitor = new AbstractMarkwonVisitorImpl(
                configuration,
                new RenderPropsImpl(),
                visitorMap);

        final SpannableBuilder builder = visitor.builder();

        append(builder, "# Header 1\n", new Object());
        append(builder, "## Header 2\n", new Object());
        append(builder, "### Header 3\n", new Object());

        final int start = builder.length();

        final FencedCodeBlock fencedCodeBlock = new FencedCodeBlock();
        fencedCodeBlock.setLiteral("{code}");

        CorePluginBridge.visitCodeBlock(visitor, null, "{code}", fencedCodeBlock);

        final int end = builder.length();

        append(builder, "### Footer 3\n", new Object());
        append(builder, "## Footer 2\n", new Object());
        append(builder, "# Footer 1\n", new Object());

        final Object[] spans = builder.spannableStringBuilder().getSpans(start, end, Object.class);

        // each character + code span
        final int length = fencedCodeBlock.getLiteral().length() + 1;
        assertEquals(length, spans.length);
        assertEquals(codeSpan, spans[0]);

        // each character
        for (int i = 1; i < length; i++) {
            assertTrue(spans[i] instanceof Highlight);
        }
    }

    private static void append(@NonNull SpannableBuilder builder, @NonNull String text, @NonNull Object span) {
        final int start = builder.length();
        builder.append(text);
        builder.setSpan(span, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}