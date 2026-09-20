package com.saproduction.command.communication;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="app.messaging.provider",havingValue="console",matchIfMissing=true)
public class ConsoleMessagingProvider implements MessagingProvider {
  private static final Logger log=LoggerFactory.getLogger(ConsoleMessagingProvider.class);
  public SendResult send(MessageCommand command){String id="console-"+command.messageId();log.info("messageId={} providerMessageId={} template={} transport=console",command.messageId(),id,command.templateKey());return new SendResult(id);}
}
