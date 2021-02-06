package org.omnaest.utils.markdown;

import org.junit.Test;

/**
 * @see MarkdownUtils
 * @author omnaest
 */
public class MarkdownUtilsTest
{

    @Test
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

}
