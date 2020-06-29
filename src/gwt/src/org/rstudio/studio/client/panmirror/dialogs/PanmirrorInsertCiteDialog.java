package org.rstudio.studio.client.panmirror.dialogs;

import org.rstudio.core.client.widget.FormListBox;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorInsertCitePreviewPair;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorInsertCiteProps;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorInsertCiteResult;
import org.rstudio.studio.client.panmirror.dialogs.model.PanmirrorInsertCiteUI;
import org.rstudio.studio.client.panmirror.server.PanmirrorCrossrefServerOperations;
import org.rstudio.studio.client.panmirror.uitools.PanmirrorUITools;
import org.rstudio.studio.client.panmirror.uitools.PanmirrorUIToolsCitation;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import jsinterop.base.Js;

public class PanmirrorInsertCiteDialog extends ModalDialog<PanmirrorInsertCiteResult>
{

   public PanmirrorInsertCiteDialog(PanmirrorInsertCiteProps citeProps,
         OperationWithInput<PanmirrorInsertCiteResult> operation)
   {
      super(title(citeProps.doi), Roles.getDialogRole(), operation, () -> {
         operation.execute(null);
      });

      RStudioGinjector.INSTANCE.injectMembers(this);
      mainWidget_ = GWT.<Binder> create(Binder.class).createAndBindUi(this);
      citeProps_ = citeProps;

      setBibliographies(citeProps.bibliographyFiles);
      previewScrollPanel_.setSize("100%", "90px");

      if (citeProps_.citeUI != null)
      {
         // We were given a fully rendered citeProps that includes the CiteUI,
         // we can just display it immediately
         onCiteUI(citeProps_.citeUI);
      }
      else
      {
         // We were given an incomplete DOI, we need to look it up
         setEnabled(false);
         canceled_ = false;

         ProgressIndicator indicator = addProgressIndicator(false);
         indicator.onProgress("Looking Up DOI..", () -> {
            canceled_ = true;
            super.closeDialog();
         });

         // Lookup the DOI using Crossref
         server_.crossrefDoi(citeProps_.doi, new ServerRequestCallback<JavaScriptObject>()
         {
            @Override
            public void onResponseReceived(JavaScriptObject response)
            {
               // User canceled the dialog, just ignore the server response
               if (canceled_)
               {
                  return;
               }

               // Get the preview and suggested Id
               citeProps_.work = Js.uncheckedCast(response);
               PanmirrorUIToolsCitation citationTools = new PanmirrorUITools().citation;
               PanmirrorInsertCiteUI citeUI = citationTools.citeUI(citeProps_);
               citeProps_.citeUI = citeUI;
               onCiteUI(citeUI);

               setEnabled(true);
               indicator.onCompleted();
            }

            @Override
            public void onError(ServerError error)
            {
               // User canceled the dialog, just ignore the server response
               if (canceled_)
               {
                  return;
               }

               indicator.onError(error.getUserMessage());
            }
         });
      }
   }

   @Override
   public void focusInitialControl()
   {
      super.focusInitialControl();
      citationId_.selectAll();
   }

   @Override
   protected PanmirrorInsertCiteResult collectInput()
   {
      PanmirrorInsertCiteResult result = new PanmirrorInsertCiteResult();
      result.id = citationId_.getText();
      result.bibliographyFile = bibliographies_.getValue(bibliographies_.getSelectedIndex());
      result.work = citeProps_.work;
      return result;
   }

   @Override
   protected Widget createMainWidget()
   {
      return mainWidget_;
   }

   @Inject
   void initialize(PanmirrorCrossrefServerOperations server)
   {
      server_ = server;
   }

   private void onCiteUI(PanmirrorInsertCiteUI citeUI)
   {
      citationId_.setText(citeUI.suggestedId);
      displayPreview(citeUI.previewFields);
   }

   private int addPreviewRow(String label, String value, int row)
   {
      if (value != null && value.length() > 0)
      {
         previewTable_.setText(row, 0, label);
         previewTable_.getFlexCellFormatter().addStyleName(row, 0,
               RES.styles().flexTablePreviewName());
         previewTable_.setText(row, 1, value);
         previewTable_.getFlexCellFormatter().addStyleName(row, 1,
               RES.styles().flexTablePreviewValue());
         return ++row;
      }
      return row;
   }

   private void setEnabled(boolean enabled)
   {
      citationId_.setEnabled(enabled);
      bibliographies_.setEnabled(enabled);
      if (enabled)
      {
         panel_.removeStyleName(RES.styles().disabled());
      }
      else
      {
         panel_.addStyleName(RES.styles().disabled());
      }

   }

   private void setBibliographies(String[] bibliographyFiles)
   {
      if (bibliographyFiles.length == 0)
      {
         // TODO: Replace this with a text, prefilled with references.bib
         // Label 'Create bibliography'
         bibliographies_.addItem("New bibliography (references.bib)", "references.bib");
      }
      else
      {
         /// 'Add to bibliography'
         for (String file : bibliographyFiles)
         {
            bibliographies_.addItem(file);
         }
      }
   }

   private void displayPreview(PanmirrorInsertCitePreviewPair[] previewPairs)
   {
      previewTable_.clear();
      // Display a preview
      int row = 0;
      for (PanmirrorInsertCitePreviewPair pair : previewPairs)
      {
         row = addPreviewRow(pair.name, pair.value, row);
      }
   }
   
   private static String title(String doi) {
      return "Citation from " + doi;       
   }

   @UiField
   Label citationLabel_;
   @UiField
   TextBox citationId_;
   @UiField
   FormListBox bibliographies_;
   @UiField
   FlexTable previewTable_;
   @UiField
   VerticalPanel panel_;
   @UiField
   ScrollPanel previewScrollPanel_;

   interface Binder extends UiBinder<Widget, PanmirrorInsertCiteDialog>
   {
   }

   private Widget mainWidget_;
   private PanmirrorCrossrefServerOperations server_;
   private boolean canceled_;
   private PanmirrorInsertCiteProps citeProps_;

   private static PanmirrorDialogsResources RES = PanmirrorDialogsResources.INSTANCE;

}
