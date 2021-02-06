package org.omnaest.utils.markdown;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.omnaest.utils.markdown.MarkdownUtils.Element;

/**
 * @see MarkdownUtils
 * @author omnaest
 */
public class MarkdownUtilsTest
{

    @Test
    @Ignore
    public void testParse() throws Exception
    {
        MarkdownUtils.parse("# Some title\nThis is *strong* but\nalso *weak* as far as I go")
                     .get()
                     .forEach(element ->
                     {
                         element.asText()
                                .ifPresent(text ->
                                {
                                    System.out.print(text.getValue());
                                });
                         element.asHeading()
                                .ifPresent(heading ->
                                {
                                    System.out.println(heading.getText());
                                });
                         element.asLineBreak()
                                .ifPresent(lb ->
                                {
                                    System.out.println();
                                });
                     });
    }

    @Test
    public void testParseHeader() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("# Title")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.iterator()
                                   .next()
                                   .asHeading()
                                   .isPresent());
        assertEquals("Title", elements.iterator()
                                      .next()
                                      .asHeading()
                                      .get()
                                      .getText());
        assertEquals(1, elements.iterator()
                                .next()
                                .asHeading()
                                .get()
                                .getStrength());
    }

    @Test
    public void testParseText() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("This is a text\nand this is the second line")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(3, elements.size());
        assertEquals(true, elements.get(0)
                                   .asText()
                                   .isPresent());
        assertEquals(true, elements.get(1)
                                   .asLineBreak()
                                   .isPresent());
        assertEquals(true, elements.get(2)
                                   .asText()
                                   .isPresent());
        assertEquals("This is a text", elements.get(0)
                                               .asText()
                                               .get()
                                               .getValue());
        assertEquals("and this is the second line", elements.get(2)
                                                            .asText()
                                                            .get()
                                                            .getValue());

    }

    @Test
    public void testParseLink() throws Exception
    {
        List<Element> elements = MarkdownUtils.parse("[Link](http://somelink.org \"Tooltip\")")
                                              .get()
                                              .collect(Collectors.toList());
        assertEquals(1, elements.size());
        assertEquals(true, elements.iterator()
                                   .next()
                                   .asLink()
                                   .isPresent());
        assertEquals("Link", elements.iterator()
                                     .next()
                                     .asLink()
                                     .get()
                                     .getLabel());
        assertEquals("http://somelink.org", elements.iterator()
                                                    .next()
                                                    .asLink()
                                                    .get()
                                                    .getLink());
        assertEquals("Tooltip", elements.iterator()
                                        .next()
                                        .asLink()
                                        .get()
                                        .getTooltip());
    }

}
