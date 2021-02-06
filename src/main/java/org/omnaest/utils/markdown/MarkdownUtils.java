package org.omnaest.utils.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.parser.Parser;

/**
 * Utilities around the markdown format of https://commonmark.org/
 * 
 * @see #parse(String)
 * @author omnaest
 */
public class MarkdownUtils
{
    public static interface Element
    {

        public default Optional<Text> asText()
        {
            return as(Text.class);
        }

        public default Optional<Heading> asHeading()
        {
            return as(Heading.class);
        }

        public default Optional<LineBreak> asLineBreak()
        {
            return as(LineBreak.class);
        }

        public default Optional<Link> asLink()
        {
            return as(Link.class);
        }

        @SuppressWarnings("unchecked")
        public default <T> Optional<T> as(Class<? extends Element> type)
        {
            return Optional.of(this)
                           .filter(element -> type.isAssignableFrom(element.getClass()))
                           .map(element -> (T) element);
        }

    }

    public static class LineBreak implements Element
    {
        @Override
        public String toString()
        {
            return "LineBreak []";
        }
    }

    public static class Text implements Element
    {
        private String  value;
        private boolean bold;

        public Text(String value, boolean bold)
        {
            super();
            this.value = value;
            this.bold = bold;
        }

        public String getValue()
        {
            return this.value;
        }

        public boolean isBold()
        {
            return this.bold;
        }

        @Override
        public String toString()
        {
            return "Text [value=" + this.value + ", bold=" + this.bold + "]";
        }

    }

    public static class Heading implements Element
    {
        private int    level;
        private String text;

        public Heading(int level, String text)
        {
            super();
            this.level = level;
            this.text = text;
        }

        public int getStrength()
        {
            return this.level;
        }

        public String getText()
        {
            return this.text;
        }

        @Override
        public String toString()
        {
            return "Heading [strength=" + this.level + ", text=" + this.text + "]";
        }

    }

    public static class Link implements Element
    {
        private String link;
        private String label;

        public Link(String link, String label)
        {
            super();
            this.link = link;
            this.label = label;
        }

        public String getLink()
        {
            return this.link;
        }

        public String getLabel()
        {
            return this.label;
        }

        @Override
        public String toString()
        {
            return "Link [link=" + this.link + ", label=" + this.label + "]";
        }

    }

    public static interface MarkdownParseResult
    {
        public Stream<Element> get();
    }

    public static MarkdownParseResult parse(String text)
    {
        Parser parser = Parser.builder()
                              .build();
        Node document = parser.parse(text);

        List<Element> elements = new ArrayList<>();

        document.accept(new AbstractVisitor()
        {
            private boolean bold = false;

            @Override
            public void visit(Emphasis emphasis)
            {
                String openingDelimiter = emphasis.getOpeningDelimiter();
                boolean isBold = org.apache.commons.lang3.StringUtils.equals("*", openingDelimiter);
                if (isBold)
                {
                    this.bold = true;
                }

                super.visit(emphasis);

                if (isBold)
                {
                    this.bold = false;
                }
            }

            @Override
            public void visit(SoftLineBreak softLineBreak)
            {
                elements.add(new LineBreak());
                super.visit(softLineBreak);
            }

            @Override
            public void visit(org.commonmark.node.Text text)
            {
                String value = text.getLiteral();
                elements.add(new Text(value, this.bold));
                super.visit(text);
            }

            @Override
            public void visit(org.commonmark.node.Heading heading)
            {
                int level = heading.getLevel();
                AtomicReference<String> textHolder = new AtomicReference<>();
                new AbstractVisitor()
                {
                    @Override
                    public void visit(org.commonmark.node.Text text)
                    {
                        textHolder.updateAndGet(currentText -> org.apache.commons.lang3.StringUtils.defaultString(currentText) + text.getLiteral());
                        super.visit(text);
                    }

                }.visit(heading);
                elements.add(new Heading(level, textHolder.get()));
            }

            @Override
            public void visit(org.commonmark.node.Link link)
            {
                elements.add(new Link(link.getDestination(), link.getTitle()));
                super.visit(link);
            }

        });

        return new MarkdownParseResult()
        {
            @Override
            public Stream<Element> get()
            {
                return elements.stream();
            }
        };
    }
}
