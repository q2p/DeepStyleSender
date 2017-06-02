package q2p.deepstylesender;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

@SuppressWarnings("serial")
final class Frame extends JFrame {
	static final void init() {}
	
	private static final byte MARGIN = 8;
	private static final short SIZE = 256;
	private static final byte HEIGHT_L = 16;
	private static final byte HEIGHT_B = 24;

	private static final SelectFileButton styleChooser = new SelectFileButton("style"), sourceChooser = new SelectFileButton("source");
	
	private static final SendButton sendButton = new SendButton();
	
	static final JFileChooser fileChooser = new JFileChooser();
	
	static final Frame frame = new Frame();
	
	static {
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setMultiSelectionEnabled(false);
	}
	
	private Frame() throws HeadlessException {
		super("DeepStyle Sender");
		setResizable(false);
		
		Container c = getContentPane();
		
		c.setPreferredSize(new Dimension(3*MARGIN+2*SIZE, 4*MARGIN+HEIGHT_L+2*HEIGHT_B));
		c.setLayout(null);

		styleChooser.link(c, MARGIN);
		sourceChooser.link(c, 2*MARGIN+SIZE);
		sendButton.link(c);
		
		pack();
		
		setLocationRelativeTo(null);
		
		setVisible(true);
	}
	
	private static final class SendButton extends JButton implements ActionListener {
		private final void link(final Container c) {
			setBounds(MARGIN, 3*MARGIN+HEIGHT_L+HEIGHT_B, MARGIN+2*SIZE, HEIGHT_B);
			c.add(this);
		}
		
		private SendButton() throws HeadlessException {
			super("Process");
			
			addActionListener(this);
		}

		public final void actionPerformed(final ActionEvent event) {
			new DeepStyleSender(styleChooser.mime, styleChooser.contains, sourceChooser.mime, sourceChooser.contains);
		}
	}

	private static class SelectFileButton implements ActionListener {
		private final String name;
		private final JButton button;
		private final JLabel label;

		public String contains = null;
		public String mime;
		
		private final void link(final Container c, final int x) {
			button.setBounds(x, MARGIN, SIZE, HEIGHT_B);
			label.setBounds(x, 2*MARGIN+HEIGHT_B, SIZE, HEIGHT_L);
			
			c.add(button);
			c.add(label);
		}
		
		private SelectFileButton(final String name) throws HeadlessException {
			this.name = name;
			
			button = new JButton("Select " + name);
			label = new JLabel(name + " not selected...");
			
			button.addActionListener(this);
		}

		public final void actionPerformed(final ActionEvent event) {
			final File file;
			
			synchronized(fileChooser) {
				fileChooser.setDialogTitle("Select " + name + " file");
				
				final int ret = fileChooser.showOpenDialog(frame);
				if(ret != JFileChooser.APPROVE_OPTION)
					return;
				
				file = fileChooser.getSelectedFile();
			}
			
			final int idx = file.getName().lastIndexOf('.')+1;
			
			if(idx == 0) {
				error(true, "Unsupported file type. Try png or jpg.");
				return;
			}
			
			switch(file.getName().substring(idx)) {
				case "png":
					mime = "png";
					break;
				case "jpg":
				case "jpeg":
					mime = "jpeg";
					break;
				default:
					error(true, "Unsupported file type. Try png or jpg.");
					return;
			}
			
			final FileInputStream fis;
			try {
				fis = new FileInputStream(file);
			} catch(IOException e1) {
				error(true, "Failed to read file.");
				return;
			}
			
			final byte[] buffer;
			
			try {
				buffer = new byte[fis.available()];
				fis.read(buffer);
			} catch(final IOException e) {
				error(true, "Failed to read file.");
				return;
			} finally {
				try {
					fis.close();
				} catch(final Exception e) {}
			}
			
			contains = Base64.getEncoder().encodeToString(buffer);
			
			label.setText(file.getName());
		}
	}
	
	static final void error(final boolean lockFrame, final String message) {
		JOptionPane.showMessageDialog(lockFrame?frame:null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}
}