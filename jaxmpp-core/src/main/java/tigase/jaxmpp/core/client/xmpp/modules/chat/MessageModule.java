/*
 * Tigase XMPP Client Library
 * Copyright (C) 2006-2012 "Bartosz Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.jaxmpp.core.client.xmpp.modules.chat;

import java.util.List;

import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.PacketWriter;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.UIDGenerator;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.factory.UniversalFactory;
import tigase.jaxmpp.core.client.observer.BaseEvent;
import tigase.jaxmpp.core.client.observer.EventType;
import tigase.jaxmpp.core.client.observer.Listener;
import tigase.jaxmpp.core.client.observer.Observable;
import tigase.jaxmpp.core.client.observer.ObservableFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

public class MessageModule extends AbstractStanzaModule<Message> {

	public static abstract class AbstractMessageEvent extends BaseEvent {

		private static final long serialVersionUID = 1L;

		private Message message;

		public AbstractMessageEvent(EventType type, SessionObject sessionObject) {
			super(type, sessionObject);
		}

		public Message getMessage() {
			return message;
		}

		public void setMessage(Message message) {
			this.message = message;
		}
	}

	public static class MessageEvent extends AbstractMessageEvent {

		private static final long serialVersionUID = 1L;

		private Chat chat;

		public MessageEvent(EventType type, SessionObject sessionObject) {
			super(type, sessionObject);
		}

		public Chat getChat() {
			return chat;
		}

		public void setChat(Chat chat) {
			this.chat = chat;
		}

	}

	public static final EventType ChatClosed = new EventType();

	public static final EventType ChatCreated = new EventType();

	public static final EventType ChatUpdated = new EventType();

	public static final Criteria CRIT = ElementCriteria.name("message");

	public static final EventType MessageReceived = new EventType();

	private final AbstractChatManager chatManager;

	private final Observable observable;

	public MessageModule(Observable parentObservable, SessionObject sessionObject, PacketWriter packetWriter) {
		super(sessionObject, packetWriter);
		this.observable = ObservableFactory.instance(parentObservable);
		AbstractChatManager cm = UniversalFactory.createInstance(AbstractChatManager.class.getName());
		this.chatManager = cm != null ? cm : new DefaultChatManager();
		this.chatManager.setObservable(this.observable);
		this.chatManager.setPacketWriter(packetWriter);
		this.chatManager.setSessionObject(sessionObject);
		this.chatManager.initialize();
	}

	public void addListener(EventType eventType, Listener<MessageEvent> listener) {
		observable.addListener(eventType, listener);
	}

	public void addListener(Listener<MessageEvent> listener) {
		observable.addListener(listener);
	}

	public void close(Chat chat) throws JaxmppException {
		chatManager.close(chat);
	}

	public Chat createChat(JID jid) throws JaxmppException {
		return this.chatManager.createChat(jid);
	}

	public AbstractChatManager getChatManager() {
		return chatManager;
	}

	public List<Chat> getChats() {
		return this.chatManager.getChats();
	}

	@Override
	public Criteria getCriteria() {
		return CRIT;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public void process(Message element) throws JaxmppException {
		MessageEvent event = new MessageEvent(MessageReceived, sessionObject);
		event.setMessage(element);
		Chat chat = chatManager.process(element, observable);
		if (chat != null) {
			event.setChat(chat);
		}
		observable.fireEvent(event.getType(), event);
	}

	public void removeListener(EventType eventType, Listener<MessageEvent> listener) {
		observable.removeListener(eventType, listener);
	}

	public void removeListener(Listener<MessageEvent> listener) {
		observable.removeListener(listener);
	}

	public void sendMessage(JID toJID, String subject, String message) throws XMLException, JaxmppException {
		Message msg = Message.create();
		msg.setSubject(subject);
		msg.setBody(message);
		msg.setTo(toJID);
		msg.setId(UIDGenerator.next());

		writer.write(msg);
	}

}