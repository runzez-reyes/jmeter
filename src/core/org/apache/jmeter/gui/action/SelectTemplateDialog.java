/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.jmeter.gui.action;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.io.IOUtils;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.template.Template;
import org.apache.jmeter.gui.action.template.TemplateManager;
import org.apache.jmeter.swing.HtmlPane;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.gui.ComponentUtil;
import org.apache.jorphan.gui.JLabeledChoice;
import org.apache.jorphan.util.JOrphanUtils;

/**
 * Dialog used for Templates selection
 * @since 2.10
 */
public class SelectTemplateDialog extends JDialog implements ChangeListener, ActionListener {

    private static final long serialVersionUID = -4436834972710248247L;
    
    // Minimal dimensions for dialog box
    private static final int MINIMAL_BOX_WIDTH = 500;
    private static final int MINIMAL_BOX_HEIGHT = 300;

//    private static final Logger log = LoggingManager.getLoggerForClass();

    private JLabeledChoice templateList;

    private final HtmlPane helpDoc = new HtmlPane();

    private final JButton applyTemplateButton = new JButton();

    private final JButton cancelButton = new JButton(JMeterUtils.getResString("cancel")); //$NON-NLS-1$;
    
    private final JScrollPane scroller = new JScrollPane(helpDoc);

    public SelectTemplateDialog() {
        super((JFrame) null, JMeterUtils.getResString("template_title"), true); //$NON-NLS-1$
        init();
    }

    @Override
    protected JRootPane createRootPane() {
        JRootPane rootPane = new JRootPane();
        // Hide Window on ESC
        Action escapeAction = new AbstractAction("ESCAPE") { //$NON-NLS-1$
            /**
             *
             */
            private static final long serialVersionUID = -6543764044868772971L;

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                setVisible(false);
            }
        };
        // Do search on Enter
        Action enterAction = new AbstractAction("ENTER") { //$NON-NLS-1$

            private static final long serialVersionUID = -3661361497864527363L;

            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                checkDirtyAndLoad(actionEvent);
            }
        };
        ActionMap actionMap = rootPane.getActionMap();
        actionMap.put(escapeAction.getValue(Action.NAME), escapeAction);
        actionMap.put(enterAction.getValue(Action.NAME), enterAction);
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStrokes.ESC, escapeAction.getValue(Action.NAME));
        inputMap.put(KeyStrokes.ENTER, enterAction.getValue(Action.NAME));

        return rootPane;
    }
    
    /**
     * Check if existing Test Plan has been modified and ask user 
     * what he wants to do if test plan is dirty
     * @param actionEvent {@link ActionEvent}
     */
    private void checkDirtyAndLoad(final ActionEvent actionEvent)
            throws HeadlessException {
        // Check if the user wants to drop any changes
        ActionRouter.getInstance().doActionNow(new ActionEvent(actionEvent.getSource(), actionEvent.getID(), ActionNames.CHECK_DIRTY));
        GuiPackage guiPackage = GuiPackage.getInstance();
        if (guiPackage.isDirty()) {
            // Check if the user wants to create from template
            int response = JOptionPane.showConfirmDialog(GuiPackage.getInstance().getMainFrame(),
                    JMeterUtils.getResString("cancel_new_from_template"), // $NON-NLS-1$
                    JMeterUtils.getResString("template_load?"),  // $NON-NLS-1$
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if(response == JOptionPane.YES_OPTION) {
                ActionRouter.getInstance().doActionNow(new ActionEvent(actionEvent.getSource(), actionEvent.getID(), ActionNames.SAVE));
            }
            if (response == JOptionPane.CLOSED_OPTION || response == JOptionPane.CANCEL_OPTION) {
                return; // Don't clear the plan
            }
        }
        ActionRouter.getInstance().doActionNow(new ActionEvent(actionEvent.getSource(), actionEvent.getID(), ActionNames.STOP_THREAD));
        doOpen(actionEvent);
    }

    private void init() {
        initializeTemplateList();
        this.getContentPane().setLayout(new BorderLayout(10, 10));
        this.getContentPane().add(templateList, BorderLayout.NORTH);
        helpDoc.setContentType("text/html"); //$NON-NLS-1$
        helpDoc.setEditable(false);
        this.getContentPane().add(scroller, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        applyTemplateButton.addActionListener(this);
        cancelButton.addActionListener(this);

        buttonsPanel.add(applyTemplateButton);
        buttonsPanel.add(cancelButton);
        this.getContentPane().add(buttonsPanel, BorderLayout.SOUTH);

        populateTemplatePage();
        this.pack();
        this.setMinimumSize(new Dimension(MINIMAL_BOX_WIDTH, MINIMAL_BOX_HEIGHT));
        ComponentUtil.centerComponentInWindow(this, 50); // center position and 50% of screen size
    }
    
    private void initializeTemplateList() {
        String[] templateNames = TemplateManager.getInstance().getTemplateNames();
        templateList = new JLabeledChoice(JMeterUtils.getResString("template_choose"), templateNames); //$NON-NLS-1$
        templateList.addChangeListener(this);
    }

    /**
     * Do search
     * @param e {@link ActionEvent}
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == cancelButton) {
            this.setVisible(false);
            return;
        }
        checkDirtyAndLoad(e);
    }
    
    @Override
    public void stateChanged(ChangeEvent event) {
        populateTemplatePage();
    }

    private void populateTemplatePage() {
        String selectedTemplate = templateList.getText();
        Template template = TemplateManager.getInstance().getTemplateByName(selectedTemplate);
        helpDoc.setText(template.getDescription());
        applyTemplateButton.setText(template.isTestPlan() 
                ? JMeterUtils.getResString("template_create_from")
                : JMeterUtils.getResString("template_merge_from") );
    }

    /**
     * @param e {@link ActionEvent}
     */
    private void doOpen(ActionEvent e) {
        final String selectedTemplate = templateList.getText();
        final Template template = TemplateManager.getInstance().getTemplateByName(selectedTemplate);   
        final String fileName = template.getFileName();
        final File fileToCopy = new File(JMeterUtils.getJMeterHome(), fileName);
        final File targetFile = new File( System.getProperty("user.dir"), 
                fileName.substring(fileName.lastIndexOf("/")));
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(fileToCopy));
            outputStream = new BufferedOutputStream(new FileOutputStream(targetFile));
            IOUtils.copy(inputStream, outputStream);
            outputStream.close();
            Load.loadProjectFile(e, targetFile, false); 
            this.setVisible(false);
        } catch (Exception e1) {
            throw new Error(e1);
        } finally {
            JOrphanUtils.closeQuietly(inputStream);
            JOrphanUtils.closeQuietly(outputStream);
        }
    }
}