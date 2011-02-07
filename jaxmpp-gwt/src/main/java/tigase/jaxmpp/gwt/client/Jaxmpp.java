package tigase.jaxmpp.gwt.client;

import java.util.Date;

import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.Connector;
import tigase.jaxmpp.core.client.Connector.ConnectorEvent;
import tigase.jaxmpp.core.client.DefaultSessionObject;
import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.JaxmppCore;
import tigase.jaxmpp.core.client.Processor;
import tigase.jaxmpp.core.client.XMPPException.ErrorCondition;
import tigase.jaxmpp.core.client.XmppSessionLogic.SessionListener;
import tigase.jaxmpp.core.client.connector.AbstractBoshConnector;
import tigase.jaxmpp.core.client.connector.ConnectorWrapper;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.logger.LogLevel;
import tigase.jaxmpp.core.client.logger.LoggerSpiFactory;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.PingModule;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule;
import tigase.jaxmpp.core.client.xmpp.modules.ResourceBinderModule.ResourceBindEvent;
import tigase.jaxmpp.core.client.xmpp.modules.disco.DiscoInfoModule;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.core.client.xmpp.utils.DateTimeFormat;
import tigase.jaxmpp.core.client.xmpp.utils.DateTimeFormat.DateTimeFormatProvider;
import tigase.jaxmpp.gwt.client.connectors.BoshConnector;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;

public class Jaxmpp extends JaxmppCore {

	private final ConnectorWrapper connectorWrapper;

	private Object lastRid;

	private RepeatingCommand timeoutChecker;

	{
		DateTimeFormat.setProvider(new DateTimeFormatProvider() {

			private final com.google.gwt.i18n.client.DateTimeFormat df1 = com.google.gwt.i18n.client.DateTimeFormat.getFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

			private final com.google.gwt.i18n.client.DateTimeFormat df2 = com.google.gwt.i18n.client.DateTimeFormat.getFormat("yyyy-MM-dd'T'HH:mm:ssZ");

			@Override
			public String format(Date date) {
				return df1.format(date);
			}

			@Override
			public Date parse(String t) {
				try {
					return df1.parse(t);
				} catch (Exception e) {
					try {
						return df2.parse(t);
					} catch (Exception e1) {
						return null;
					}
				}
			}
		});
	}

	public Jaxmpp() {
		this(new DefaultLoggerSpi());
		this.timeoutChecker = new RepeatingCommand() {

			@Override
			public boolean execute() {
				try {
					checkTimeouts();
				} catch (Exception e) {
				}
				return true;
			}
		};
		Scheduler.get().scheduleFixedDelay(timeoutChecker, 1000 * 31);
	}

	public Jaxmpp(LoggerSpiFactory defaultLoggerSpi) {
		super(defaultLoggerSpi);

		this.connectorWrapper = new ConnectorWrapper();
		this.connector = this.connectorWrapper;

		this.connector.addListener(Connector.StanzaReceived, this.stanzaReceivedListener);
		this.connector.addListener(Connector.StreamTerminated, this.streamTerminateListener);
		this.connector.addListener(Connector.Error, this.streamErrorListener);

		this.sessionObject = new DefaultSessionObject();
		this.processor = new Processor(this.modulesManager, this.sessionObject, this.writer);

		sessionObject.setProperty(DiscoInfoModule.IDENTITY_TYPE_KEY, "web");

		modulesInit();

		ResourceBinderModule r = this.modulesManager.getModule(ResourceBinderModule.class);
		r.addListener(ResourceBinderModule.ResourceBindSuccess, resourceBindListener);
	}

	protected void checkTimeouts() {
		sessionObject.checkHandlersTimeout();
		if (isConnected()) {
			Object r = sessionObject.getProperty(AbstractBoshConnector.RID_KEY);
			if (lastRid != null && lastRid.equals(r)) {
				Scheduler.get().scheduleDeferred(new ScheduledCommand() {

					@Override
					public void execute() {
						JID jid = sessionObject.getProperty(ResourceBinderModule.BINDED_RESOURCE_JID);
						try {
							GWT.log("Checking if server lived");
							modulesManager.getModule(PingModule.class).ping(JID.jidInstance(jid.getDomain()),
									new AsyncCallback() {

										@Override
										public void onError(Stanza responseStanza, ErrorCondition error) throws XMLException {
										}

										@Override
										public void onSuccess(Stanza responseStanza) throws XMLException {
										}

										@Override
										public void onTimeout() throws XMLException {
											try {
												disconnect();
											} catch (JaxmppException e) {
												onException(e);
											}
										}
									});
						} catch (Exception e) {
							onException(new JaxmppException(e));
						}
					}
				});
			}
			lastRid = r;
		}
	}

	@Override
	public void disconnect() throws JaxmppException {
		try {
			this.connector.stop();
		} catch (XMLException e) {
			throw new JaxmppException(e);
		}
	}

	@Override
	public void login() throws JaxmppException {
		lastRid = null;
		this.sessionObject.clear();

		if (this.sessionLogic != null) {
			this.sessionLogic.unbind();
			this.sessionLogic = null;
		}

		this.connectorWrapper.setConnector(new BoshConnector(this.sessionObject));

		this.sessionLogic = connector.createSessionLogic(modulesManager, this.writer);
		this.sessionLogic.bind(new SessionListener() {

			@Override
			public void onException(JaxmppException e) {
				Jaxmpp.this.onException(e);
			}
		});

		try {
			this.connector.start();
		} catch (XMLException e1) {
			throw new JaxmppException(e1);
		}
	}

	@Override
	protected void onException(JaxmppException e) {
		log.log(LogLevel.FINE, "Catching exception", e);
		try {
			connector.stop();
		} catch (Exception e1) {
			log.log(LogLevel.FINE, "Disconnecting error", e1);
		}
		JaxmppEvent event = new JaxmppEvent(Disconnected);
		event.setCaught(e);
		observable.fireEvent(event);
	}

	@Override
	protected void onResourceBinded(ResourceBindEvent be) {
		JaxmppEvent event = new JaxmppEvent(Connected);
		observable.fireEvent(event);
	}

	@Override
	protected void onStanzaReceived(Element stanza) {
		final Runnable r = this.processor.process(stanza);
		if (r != null)
			Scheduler.get().scheduleDeferred(new ScheduledCommand() {

				@Override
				public void execute() {
					r.run();
				}
			});
	}

	@Override
	protected void onStreamError(ConnectorEvent be) {
		JaxmppEvent event = new JaxmppEvent(Disconnected);
		event.setCaught(be.getCaught());
		observable.fireEvent(event);
	}

	@Override
	protected void onStreamTerminated(ConnectorEvent be) {
		JaxmppEvent event = new JaxmppEvent(Disconnected);
		event.setCaught(be.getCaught());
		observable.fireEvent(event);
	}

	@Override
	public void send(Stanza stanza) throws XMLException, JaxmppException {
		this.writer.write(stanza);
	}

	@Override
	public void send(Stanza stanza, AsyncCallback asyncCallback) throws XMLException, JaxmppException {
		this.sessionObject.registerResponseHandler(stanza, asyncCallback);
		this.writer.write(stanza);
	}

}
