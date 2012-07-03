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
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves as a facade to the 'show quick doc on mouse over an element' functionality.
 * <p/>
 * Not thread-safe.
 *
 * @author Denis Zhdanov
 * @since 7/2/12 9:09 AM
 */
public class QuickDocOnMouseOverManager {
  
  private static final long QUICK_DOC_DELAY_MILLIS;
  static {
    long delay = 500;
    String property = System.getProperty("editor.auto.quick.doc.delay.ms");
    if (property != null) {
      try {
        long parsed = Long.parseLong(property);
        if (parsed > 0) {
          delay = parsed;
        }
      }
      catch (Exception e) {
        // Ignore.
      }
    }
    QUICK_DOC_DELAY_MILLIS = delay;
  }
  
  @NotNull private final EditorMouseMotionListener myEditorListener    = new MyEditorMouseListener();
  @NotNull private final Alarm                     myAlarm             = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull private final Runnable                  myRequest           = new MyShowQuickDocRequest();
  @NotNull private final Runnable                  myHintCloseCallback = new Runnable() {
    @Override
    public void run() {
      myActiveElements.clear();
      myDocumentationManager = null;
    }
  };

  private final Map<Editor, PsiElement /** PSI element which is located under the current mouse position */> myActiveElements
    = new HashMap<Editor, PsiElement>();

  /** Holds a reference (if any) to the documentation manager used last time to show an 'auto quick doc' popup. */
  @Nullable private WeakReference<DocumentationManager> myDocumentationManager;

  @Nullable private DelayedQuickDocInfo myDelayedQuickDocInfo;
  private           boolean             myEnabled;

  public QuickDocOnMouseOverManager(@NotNull Application application) {
    EditorFactory factory = EditorFactory.getInstance();
    if (factory != null) {
      factory.addEditorFactoryListener(new MyEditorFactoryListener(), application);
    }
  }

  /**
   * Instructs the manager to enable or disable 'show quick doc automatically when the mouse goes over an editor element' mode.
   *
   * @param enabled  flag that identifies if quick doc should be automatically shown
   */
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
    if (!enabled) {
      closeAutoQuickDocComponentIfNecessary();
      myAlarm.cancelAllRequests();
    }
    EditorFactory factory = EditorFactory.getInstance();
    if (factory == null) {
      return;
    }
    for (Editor editor : factory.getAllEditors()) {
      if (enabled) {
        editor.addEditorMouseMotionListener(myEditorListener);
      }
      else {
        editor.removeEditorMouseMotionListener(myEditorListener);
      }
    }
  }

  private void processMouseMove(@NotNull EditorMouseEvent e) {
    if (e.getArea() != EditorMouseEventArea.EDITING_AREA) {
      // Skip if the mouse is not at the editing area.
      closeAutoQuickDocComponentIfNecessary();
      return;
    }
    
    Editor editor = e.getEditor();
    Project project = editor.getProject();
    if (project == null) {
      return;
    }

    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    JBPopup hint = documentationManager.getDocInfoHint();
    if (hint != null) {

      // Skip the event if the control is shown because of explicit 'show quick doc' action call.
      WeakReference<DocumentationManager> ref = myDocumentationManager;
      if (ref == null || ref.get() == null) {
        return;
      }

      // Skip the event if the mouse is under the opened quick doc control.
      Point hintLocation = hint.getLocationOnScreen();
      Dimension hintSize = hint.getSize();
      int mouseX = e.getMouseEvent().getXOnScreen();
      int mouseY = e.getMouseEvent().getYOnScreen();
      if (mouseX >= hintLocation.x && mouseX <= hintLocation.x + hintSize.width && mouseY >= hintLocation.y
          && mouseY <= hintLocation.y + hintSize.height)
      {
        return;
      }
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      closeAutoQuickDocComponentIfNecessary();
      return;
    }
    
    int mouseOffset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(e.getMouseEvent().getPoint()));
    PsiElement elementUnderMouse = psiFile.findElementAt(mouseOffset);
    if (elementUnderMouse == null || elementUnderMouse instanceof PsiWhiteSpace) {
      closeAutoQuickDocComponentIfNecessary();
      return;
    }
    
    PsiElement targetElementUnderMouse = documentationManager.findTargetElement(editor, mouseOffset, psiFile, elementUnderMouse);
    if (targetElementUnderMouse == null) {
      // No PSI element is located under the current mouse position - close quick doc if any.
      closeAutoQuickDocComponentIfNecessary();
      return;
    }

    PsiElement activeElement = myActiveElements.get(editor);
    if (targetElementUnderMouse.equals(activeElement)
        && (myAlarm.getActiveRequestCount() > 0 // Request to show documentation for the target component has been already queued.
            || hint != null)) // Documentation for the target component is being shown.
    { 
      return;
    }
    closeAutoQuickDocComponentIfNecessary();
    myActiveElements.put(editor, targetElementUnderMouse);
    myDelayedQuickDocInfo = new DelayedQuickDocInfo(documentationManager, editor, targetElementUnderMouse, elementUnderMouse);

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myRequest, QUICK_DOC_DELAY_MILLIS);
  }

  private void closeAutoQuickDocComponentIfNecessary() {
    myAlarm.cancelAllRequests();
    WeakReference<DocumentationManager> ref = myDocumentationManager;
    if (ref == null) {
      return;
    }

    DocumentationManager docManager = ref.get();
    if (docManager == null) {
      return;
    }

    JBPopup hint = docManager.getDocInfoHint();
    if (hint == null) {
      return;
    }
    
    hint.cancel();
  }

  private static class DelayedQuickDocInfo {

    @NotNull public final DocumentationManager docManager;
    @NotNull public final Editor               editor;
    @NotNull public final PsiElement           targetElement;
    @NotNull public final PsiElement           originalElement;

    private DelayedQuickDocInfo(@NotNull DocumentationManager docManager,
                                @NotNull Editor editor, @NotNull PsiElement targetElement,
                                @NotNull PsiElement originalElement)
    {
      this.docManager = docManager;
      this.editor = editor;
      this.targetElement = targetElement;
      this.originalElement = originalElement;
    }
  }

  private class MyShowQuickDocRequest implements Runnable {
    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      
      DelayedQuickDocInfo info = myDelayedQuickDocInfo;
      if (info == null || !info.targetElement.equals(myActiveElements.get(info.editor))) {
        return;
      }
      
      info.editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION,
                              info.editor.offsetToVisualPosition(info.originalElement.getTextRange().getStartOffset()));
      try {
        info.docManager.showJavaDocInfo(info.editor, info.targetElement, info.originalElement, myHintCloseCallback);
        myDocumentationManager = new WeakReference<DocumentationManager>(info.docManager);
      }
      finally {
        info.editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
      }
    }
  }

  private class MyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      if (myEnabled) {
        event.getEditor().addEditorMouseMotionListener(myEditorListener);
      }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      event.getEditor().removeEditorMouseMotionListener(myEditorListener);
    }
  }

  private class MyEditorMouseListener extends EditorMouseMotionAdapter {

    @Override
    public void mouseMoved(EditorMouseEvent e) {
      processMouseMove(e);
    }
  }
}