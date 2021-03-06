/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class DetectedFrameworksComponent {
  private JPanel myMainPanel;
  private final DetectedFrameworksTree myTree;
  private JPanel myTreePanel;
  private JPanel myOptionsPanel;
  private Splitter mySplitter;
  private JComboBox myGroupByComboBox;
  private JLabel myDescriptionLabel;

  public DetectedFrameworksComponent(final FrameworkDetectionContext context) {
    myTree = new DetectedFrameworksTree(context, GroupByOption.TYPE) {
      @Override
      protected void onNodeStateChanged(CheckedTreeNode node) {
        super.onNodeStateChanged(node);
        updateOptionsPanel();
      }
    };
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    myGroupByComboBox.setModel(new EnumComboBoxModel<GroupByOption>(GroupByOption.class));
    myGroupByComboBox.setRenderer(new GroupByListCellRenderer());
    myGroupByComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTree.changeGroupBy((GroupByOption)myGroupByComboBox.getSelectedItem());
      }
    });
    myTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        updateOptionsPanel();
      }
    });
    updateOptionsPanel();
  }

  public DetectedFrameworksTree getTree() {
    return myTree;
  }

  private void updateOptionsPanel() {
    final DetectedFrameworkTreeNodeBase[] nodes = myTree.getSelectedNodes(DetectedFrameworkTreeNodeBase.class, null);
    if (nodes.length == 1) {
      final DetectedFrameworkTreeNodeBase node = nodes[0];
      String description = node.isChecked() ? node.getCheckedDescription() : node.getUncheckedDescription();
      if (description != null) {
        myDescriptionLabel.setText(UIUtil.toHtml(description));
        return;
      }
    }
    myDescriptionLabel.setText("");
  }

  public List<DetectedFrameworkDescription> getSelectedFrameworks() {
    return Arrays.asList(myTree.getCheckedNodes(DetectedFrameworkDescription.class, null));
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  public void processUncheckedNodes(final DetectionExcludesConfiguration excludesConfiguration) {
    getTree().processUncheckedNodes(new Consumer<DetectedFrameworkTreeNodeBase>() {
      @Override
      public void consume(DetectedFrameworkTreeNodeBase node) {
        node.disableDetection(excludesConfiguration);
      }
    });
  }

  public static enum GroupByOption { TYPE, DIRECTORY }

  private class GroupByListCellRenderer extends ListCellRendererWrapper<GroupByOption> {
    public GroupByListCellRenderer() {
      super();
    }

    @Override
    public void customize(JList list,
                          GroupByOption value,
                          int index,
                          boolean selected,
                          boolean hasFocus) {
      if (value != null) {
        setText(value.name().toLowerCase());
      }
    }
  }
}
