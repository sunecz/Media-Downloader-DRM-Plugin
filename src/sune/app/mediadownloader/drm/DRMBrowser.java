package sune.app.mediadownloader.drm;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.slf4j.Logger;

import sune.app.mediadownloader.drm.util.DRMUtils.BrowserAccessor;
import sune.app.mediadownloader.drm.util.DRMUtils.JSRequest;
import sune.app.mediadownloader.drm.util.StateMutex;

public class DRMBrowser extends JFrame {
	
	private static final long serialVersionUID = 1491787442060245403L;
	
	private static final Logger logger = DRMLog.get();
	
	private final DRMClient client;
	private volatile boolean isClosed = false;
	private CefBrowser browser;
	private BrowserAccessor accessor;
	private DRMBrowserContext context;
	
	private StateMutex mtxClose = new StateMutex();
	
	DRMBrowser(DRMContext drmContext, DRMClient drmClient, int width, int height) {
		client = drmClient;
		browser = client.cefClient().createBrowser("about:blank", false, false, null);
		// Add the broswer component to the window
		Component component = browser.getUIComponent();
		component.setPreferredSize(new Dimension(width, height));
		component.setFocusable(false);
		component.addComponentListener(new ComponentAdapter() {
			
			@Override
			public void componentMoved(ComponentEvent e) {
				// Disable focus
				component.setFocusable(false);
			}
			
			@Override
			public void componentResized(ComponentEvent e) {
				// Disable focus
				component.setFocusable(false);
			}
			
			@Override
			public void componentShown(ComponentEvent e) {
				// Disable focus
				component.setFocusable(false);
			}
		});
		JPanel contentPanel = new JPanel(new BorderLayout());
		contentPanel.add(component, BorderLayout.CENTER);
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		addComponentListener(new ComponentAdapter() {
			
			@Override
			public void componentMoved(ComponentEvent e) {
				// Make the window non-movable
				setLocation(0, 0);
				// Disable focus
				setFocusable(false);
				// Make the window always in the back
				toBack();
			}
			
			@Override
			public void componentResized(ComponentEvent e) {
				// Disable focus
				setFocusable(false);
				// Make the window always in the back
				toBack();
			}
			
			@Override
			public void componentShown(ComponentEvent e) {
				// Disable focus
				setFocusable(false);
				// Make the window always in the back
				toBack();
			}
		});
		addWindowListener(new WindowAdapter() {
			
			@Override
			public void windowIconified(WindowEvent e) {
				// Make the window non-minimizable
				setExtendedState(JFrame.NORMAL);
				// Make the window always in the back
				toBack();
			}
			
			@Override
			public void windowActivated(WindowEvent e) {
				// Make the window always in the back
				toBack();
			}
			
			@Override
			public void windowOpened(WindowEvent e) {
				// Make the window always in the back
				toBack();
			}
			
			@Override
			public void windowGainedFocus(WindowEvent e) {
				// Disable focus
				setFocusable(false);
				// Make the window always in the back
				toBack();
			}
			
			@Override
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				try {
					if(browser == null) {
						isClosed = true;
						dispose();
					} else {
						if(isClosed)
							browser.setCloseAllowed();
						browser.close(isClosed);
						if(isClosed)
							dispose();
						isClosed = true;
					}
				} finally {
					mtxClose.unlock();
				}
			}
		});
		setLocationRelativeTo(null);
		setLocationByPlatform(false);
		setResizable(false);
		setUndecorated(true);
		setFocusableWindowState(false);
		setAutoRequestFocus(false);
		setFocusable(false);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setTitle(String.format(DRMConstants.FRAME_TITLE_FORMAT, drmContext.uuid().toString()));
		pack(); // Make sure the broswer fits into the window
		setLocation(0, 0);
		toBack(); // Make the window always in the back
	}
	
	// Shortcut method
	public void addJSRequest(CefFrame frame, JSRequest result) {
		client.addJSRequest(frame, result);
	}
	
	private final void close() {
		// This solves an issue with infrequent seemingly random crashes of libcef.dll
		// For more info see: https://www.magpcss.org/ceforum/viewtopic.php?f=6&t=13044
		
		String urlToLoad = "about:blank";
		client.loadNotifier().enable(urlToLoad);
		
		if(logger.isDebugEnabled())
			logger.debug("Loading \"{}\" before closing to avoid crashes...", urlToLoad);
		
		load(urlToLoad);
		client.loadNotifier().await(urlToLoad);

		if(logger.isDebugEnabled())
			logger.debug("Loaded \"{}\". Dispatching closing event...", urlToLoad);
		
		SwingUtilities.invokeLater(() -> {
			dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
		});
		mtxClose.await();
	}
	
	public void start() {
		SwingUtilities.invokeLater(() -> {
			setVisible(true);
			// Make sure the browser is showing
			revalidate();
			repaint();
		});
	}
	
	public void load(String url) {
		SwingUtilities.invokeLater(() -> {
			browser.stopLoad();
			browser.loadURL(url);
		});
	}
	
	public DRMClient client() {
		return client;
	}
	
	public CefBrowser cefBrowser() {
		return browser;
	}
	
	public BrowserAccessor accessor() {
		if(accessor == null) {
			accessor = new BrowserAccessor(browser);
		}
		return accessor;
	}
	
	public final DRMBrowserContext context() {
		if(context == null) {
			context = new Context();
		}
		return context;
	}
	
	private final class Context implements DRMBrowserContext {
		
		@Override
		public void close() {
			DRMBrowser.this.close();
		}
		
		@Override
		public DRMBrowser browser() {
			return DRMBrowser.this;
		}
		
		@Override
		public String title() {
			return DRMBrowser.this.getTitle();
		}
	}
}