// Copyright 2009 Formos
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.formos.tapestry.templating.internal.services;

import com.formos.tapestry.templating.ContentStream;
import com.formos.tapestry.templating.RenderedStream;
import com.formos.tapestry.templating.RenderedStreamEnclosure;
import com.formos.tapestry.templating.services.MailMessagePreparer;
import org.apache.tapestry5.ioc.internal.util.Defense;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MailMessagePreparerImpl implements MailMessagePreparer
{
    private static class ContentStreamDataSource implements DataSource
    {
        private final ContentStream contentStream;

        private final String name;

        private final String contentType;

        public ContentStreamDataSource(ContentStream contentStream, String name)
        {
            this(contentStream, name, contentStream.getContentType());
        }

        public ContentStreamDataSource(ContentStream contentStream, String name, String contentType)
        {
            this.contentStream = contentStream;
            this.name = name;
            this.contentType = contentType;
        }

        public InputStream getInputStream() throws IOException
        {
            return contentStream.getStream();
        }

        public OutputStream getOutputStream() throws IOException
        {
            throw new UnsupportedOperationException("Stream is output only when constructing mail message.");
        }

        public String getContentType()
        {
            return contentType;
        }

        public String getName()
        {
            return name;
        }
    }

    public void prepareMessage(Message message, RenderedStream renderedStream) throws MessagingException
    {
        Defense.notNull(message, "message");
        Defense.notNull(renderedStream, "renderedStream");

        // This is a little inefficient when there is no extra content.

        Multipart multipart = new MimeMultipart();

        MimeBodyPart body = new MimeBodyPart();

        body.setDataHandler(new DataHandler(new ContentStreamDataSource(renderedStream, "main")));

        multipart.addBodyPart(body);

        for (RenderedStreamEnclosure enclosure : renderedStream.getEnclosures())
        {
            String contentId = enclosure.getContentID();

            MimeBodyPart part = new MimeBodyPart();

            DataSource source = new ContentStreamDataSource(enclosure, contentId);

            part.setDataHandler(new DataHandler(source));
            part.setFileName(contentId);
            part.setDisposition(Part.INLINE);
            part.setContentID("<" + contentId + ">");

            multipart.addBodyPart(part);
        }


        message.setContent(multipart);
    }

}
