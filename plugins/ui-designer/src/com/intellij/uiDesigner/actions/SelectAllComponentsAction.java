/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.propertyInspector.UIDesignerToolWindowManager;
import com.intellij.uiDesigner.componentTree.ComponentTreeBuilder;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.List;

/**
 * @author yole
 */
public class SelectAllComponentsAction extends AbstractGuiEditorAction {
  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    final ComponentTreeBuilder builder = UIDesignerToolWindowManager.getInstance(editor.getProject()).getComponentTreeBuilder();
    builder.beginUpdateSelection();
    try {
      FormEditingUtil.iterate(editor.getRootContainer(), new FormEditingUtil.ComponentVisitor() {
        public boolean visit(final IComponent component) {
          ((RadComponent) component).setSelected(true);
          return true;
        }
      });
    }
    finally {
      builder.endUpdateSelection();
    }
  }
}