// Copyright 2010 Howard M. Lewis Ship
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.howardlewisship.tapx.core.internal.dynamic;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.apache.tapestry5.Block;
import org.apache.tapestry5.MarkupWriter;
import org.apache.tapestry5.PropertyConduit;
import org.apache.tapestry5.func.F;
import org.apache.tapestry5.func.Flow;
import org.apache.tapestry5.func.Mapper;
import org.apache.tapestry5.func.Worker;
import org.apache.tapestry5.internal.parser.AttributeToken;
import org.apache.tapestry5.internal.services.XMLTokenStream;
import org.apache.tapestry5.internal.services.XMLTokenType;
import org.apache.tapestry5.ioc.Location;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.internal.util.InternalUtils;
import org.apache.tapestry5.ioc.internal.util.TapestryException;
import org.apache.tapestry5.runtime.RenderCommand;
import org.apache.tapestry5.runtime.RenderQueue;
import org.apache.tapestry5.services.PropertyConduitSource;

import com.howardlewisship.tapx.core.dynamic.DynamicDelegate;
import com.howardlewisship.tapx.core.dynamic.DynamicTemplate;

/** Does the heavy lifting for {@link DynamicTemplateParserImpl}. */
class DynamicTemplateSaxParser
{
    private final Resource resource;

    private final PropertyConduitSource propertyConduitSource;

    private final XMLTokenStream tokenStream;

    private final Map<String, URL> publicIdToURL = Collections.emptyMap();

    private static final Pattern PARAM_ID_PATTERN = Pattern.compile("^param:(\\p{Alpha}\\w*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXPANSION_PATTERN = Pattern.compile("\\$\\{\\s*(.*?)\\s*}");

    private static final DynamicTemplateElement END = new DynamicTemplateElement()
    {
        public void render(MarkupWriter writer, RenderQueue queue, DynamicDelegate delegate)
        {
            // End the previously started element
            writer.end();
        }
    };

    DynamicTemplateSaxParser(Resource resource, PropertyConduitSource propertyConduitSource)
    {
        this.resource = resource;
        this.propertyConduitSource = propertyConduitSource;

        this.tokenStream = new XMLTokenStream(resource, publicIdToURL);
    }

    DynamicTemplate parse()
    {
        try
        {
            tokenStream.parse();

            return toDynamicTemplate(root());
        }
        catch (Exception ex)
        {
            throw new TapestryException(String.format("Failure parsing dynamic template %s: %s", resource,
                    InternalUtils.toMessage(ex)), tokenStream.getLocation(), ex);
        }
    }

    // Note the use of static methods; otherwise the compiler sets this$0 to point to the DynamicTemplateSaxParser,
    // creating an unwanted reference that keeps the parser from being GCed.

    private static DynamicTemplate toDynamicTemplate(List<DynamicTemplateElement> elements)
    {
        final Flow<DynamicTemplateElement> flow = F.flow(elements).reverse();

        return new DynamicTemplate()
        {
            public RenderCommand createRenderCommand(final DynamicDelegate delegate)
            {
                final Mapper<DynamicTemplateElement, RenderCommand> toRenderCommand = createToRenderCommandMapper(delegate);

                return new RenderCommand()
                {
                    public void render(MarkupWriter writer, RenderQueue queue)
                    {
                        Worker<RenderCommand> pushOnQueue = createQueueRenderCommand(queue);

                        flow.map(toRenderCommand).each(pushOnQueue);
                    }
                };
            }
        };
    }

    private List<DynamicTemplateElement> root()
    {
        List<DynamicTemplateElement> result = CollectionFactory.newList();

        while (tokenStream.hasNext())
        {
            switch (tokenStream.next())
            {
                case START_ELEMENT:
                    result.add(element());
                    break;

                case END_DOCUMENT:
                    // Ignore it.
                    break;

                default:
                    addTextContent(result);
            }
        }

        return result;
    }

    private DynamicTemplateElement element()
    {
        String elementURI = tokenStream.getNamespaceURI();
        String elementName = tokenStream.getLocalName();

        String blockId = null;

        int count = tokenStream.getAttributeCount();

        List<AttributeToken> attributes = CollectionFactory.newList();

        Location location = getLocation();

        for (int i = 0; i < count; i++)
        {
            QName qname = tokenStream.getAttributeName(i);

            // The name will be blank for an xmlns: attribute

            String localName = qname.getLocalPart();

            if (InternalUtils.isBlank(localName))
                continue;

            String uri = qname.getNamespaceURI();

            String value = tokenStream.getAttributeValue(i);

            if (localName.equals("id"))
            {
                Matcher matcher = PARAM_ID_PATTERN.matcher(value);

                if (matcher.matches())
                {
                    blockId = matcher.group(1);
                    continue;
                }
            }

            attributes.add(new AttributeToken(uri, localName, value, location));
        }

        if (blockId != null)
            return block(blockId);

        List<DynamicTemplateElement> body = CollectionFactory.newList();

        boolean atEnd = false;
        while (!atEnd)
        {
            switch (tokenStream.next())
            {
                case START_ELEMENT:

                    // Recurse into this new element
                    body.add(element());

                    break;

                case END_ELEMENT:
                    body.add(END);
                    atEnd = true;

                    break;

                default:

                    addTextContent(body);
            }
        }

        return createElementWriterElement(elementURI, elementName, attributes, body);
    }

    private static DynamicTemplateElement createElementWriterElement(final String elementURI, final String elementName,
            final List<AttributeToken> attributes, List<DynamicTemplateElement> body)
    {
        final Flow<DynamicTemplateElement> bodyFlow = F.flow(body).reverse();

        return new DynamicTemplateElement()
        {
            public void render(MarkupWriter writer, RenderQueue queue, DynamicDelegate delegate)
            {
                // Write the element ...

                writer.elementNS(elementURI, elementName);

                // ... and the attributes

                for (AttributeToken attribute : attributes)
                {
                    writer.attributeNS(attribute.getNamespaceURI(), attribute.getName(), attribute.getValue());
                }

                // And convert the DTEs for the direct children of this element into RenderCommands and push them onto
                // the queue. This includes the child that will end the started element.

                Mapper<DynamicTemplateElement, RenderCommand> toRenderCommand = createToRenderCommandMapper(delegate);
                Worker<RenderCommand> pushOnQueue = createQueueRenderCommand(queue);

                bodyFlow.map(toRenderCommand).each(pushOnQueue);
            }
        };
    }

    private DynamicTemplateElement block(final String blockId)
    {
        Location location = getLocation();

        removeContent();

        return createBlockElement(blockId, location);
    }

    private static DynamicTemplateElement createBlockElement(final String blockId, final Location location)
    {
        return new DynamicTemplateElement()
        {
            public void render(MarkupWriter writer, RenderQueue queue, DynamicDelegate delegate)
            {
                try
                {
                    Block block = delegate.getBlock(blockId);

                    queue.push((RenderCommand) block);
                }
                catch (Exception ex)
                {
                    throw new TapestryException(String.format(
                            "Exception rendering block '%s' as part of dynamic template: %s", blockId,
                            InternalUtils.toMessage(ex)), location, ex);
                }
            }
        };
    }

    private Location getLocation()
    {
        return tokenStream.getLocation();
    }

    private void removeContent()
    {
        int depth = 1;

        while (true)
        {
            switch (tokenStream.next())
            {
                case START_ELEMENT:
                    depth++;
                    break;

                // The matching end element.

                case END_ELEMENT:
                    depth--;

                    if (depth == 0)
                        return;

                    break;

                default:
                    // Ignore anything else (text, comments, etc.)
            }
        }
    }

    void addTextContent(List<DynamicTemplateElement> elements)
    {
        switch (tokenStream.getEventType())
        {
            case COMMENT:
                elements.add(comment());
                break;

            case CHARACTERS:
            case SPACE:
                addTokensForText(elements);
                break;

            default:
                unexpectedEventType();
        }
    }

    private void addTokensForText(List<DynamicTemplateElement> elements)
    {
        Location location = tokenStream.getLocation();

        String text = tokenStream.getText();

        Matcher matcher = EXPANSION_PATTERN.matcher(text);

        int startx = 0;

        while (matcher.find())
        {
            int matchStart = matcher.start();

            if (matchStart != startx)
            {
                String prefix = text.substring(startx, matchStart);

                elements.add(createTextWriterElement(prefix));
            }

            // Group 1 includes the real text of the expansion, with whitespace
            // around the
            // expression (but inside the curly braces) excluded.

            String expression = matcher.group(1);

            elements.add(createExpansionElement(expression, location, propertyConduitSource));

            startx = matcher.end();
        }

        // Catch anything after the final regexp match.

        if (startx < text.length())
            elements.add(createTextWriterElement(text.substring(startx, text.length())));
    }

    private DynamicTemplateElement comment()
    {
        return createCommentElement(tokenStream.getText());
    }

    private static DynamicTemplateElement createCommentElement(final String content)
    {
        return new DynamicTemplateElement()
        {
            public void render(MarkupWriter writer, RenderQueue queue, DynamicDelegate delegate)
            {
                writer.comment(content);
            }
        };
    }

    private static DynamicTemplateElement createTextWriterElement(final String content)
    {
        return new DynamicTemplateElement()
        {
            public void render(MarkupWriter writer, RenderQueue queue, DynamicDelegate delegate)
            {
                writer.write(content);
            }
        };
    }

    private static DynamicTemplateElement createExpansionElement(final String expression, final Location location,
            final PropertyConduitSource conduitSource)
    {
        return new DynamicTemplateElement()
        {
            public void render(MarkupWriter writer, RenderQueue queue, DynamicDelegate delegate)
            {
                Object expressionRoot = delegate.getExpressionRoot();

                try
                {
                    PropertyConduit conduit = conduitSource.create(expressionRoot.getClass(), expression);

                    Object value = conduit.get(expressionRoot);

                    if (value != null)
                        writer.write(value.toString());
                }
                catch (Throwable t)
                {
                    throw new TapestryException(InternalUtils.toMessage(t), location, t);
                }
            }
        };
    }

    private <T> T unexpectedEventType()
    {
        XMLTokenType eventType = tokenStream.getEventType();

        throw new IllegalStateException(String.format("Unexpected XML parse event %s.", eventType.name()));
    }

    private static Worker<RenderCommand> createQueueRenderCommand(final RenderQueue queue)
    {
        return new Worker<RenderCommand>()
        {
            public void work(RenderCommand value)
            {
                queue.push(value);
            }
        };
    }

    private static RenderCommand toRenderCommand(final DynamicTemplateElement value, final DynamicDelegate delegate)
    {
        return new RenderCommand()
        {
            public void render(MarkupWriter writer, RenderQueue queue)
            {
                value.render(writer, queue, delegate);
            }
        };
    }

    private static Mapper<DynamicTemplateElement, RenderCommand> createToRenderCommandMapper(
            final DynamicDelegate delegate)
    {
        return new Mapper<DynamicTemplateElement, RenderCommand>()
        {
            public RenderCommand map(final DynamicTemplateElement value)
            {
                return toRenderCommand(value, delegate);
            }
        };
    }
}