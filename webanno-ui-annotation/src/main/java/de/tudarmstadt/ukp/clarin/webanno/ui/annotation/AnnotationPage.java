/*
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation;

import static de.tudarmstadt.ukp.clarin.webanno.api.SecurityUtil.annotationEnabeled;
import static de.tudarmstadt.ukp.clarin.webanno.api.SecurityUtil.isAdmin;
import static de.tudarmstadt.ukp.clarin.webanno.api.SecurityUtil.isAnnotator;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.PAGE_PARAM_DOCUMENT_ID;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.PAGE_PARAM_PROJECT_ID;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectByAddr;
import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.persistence.NoResultException;

import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.CssContentHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wicketstuff.annotation.mount.MountPath;
import org.wicketstuff.urlfragment.UrlFragment;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectType;
import de.tudarmstadt.ukp.clarin.webanno.api.SettingsService;
import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorExtensionRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorFactory;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.constraints.ConstraintsService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentStateTransition;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.dialog.ConfirmationDialog;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.ActionBarLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxSubmitLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaModel;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.DecoratedObject;
import de.tudarmstadt.ukp.clarin.webanno.support.wicketstuff.UrlParametersReceivingBehavior;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.FinishImage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.detail.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.AnnotationPreferencesDialog;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.ExportDocumentDialog;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.GuidelinesDialog;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.dialog.OpenDocumentDialog;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.SidebarPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItem;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItemCondition;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import wicket.contrib.input.events.EventType;
import wicket.contrib.input.events.InputBehavior;
import wicket.contrib.input.events.key.KeyType;

/**
 * A wicket page for the Brat Annotation/Visualization page. Included components for pagination,
 * annotation layer configuration, and Exporting document
 */
@MenuItem(icon = "images/categories.png", label = "Annotation", prio = 100)
@MountPath(value = "/annotation.html", alt = { "/annotate/${" + PAGE_PARAM_PROJECT_ID + "}",
        "/annotate/${" + PAGE_PARAM_PROJECT_ID + "}/${" + PAGE_PARAM_DOCUMENT_ID + "}" })
@ProjectType(id = WebAnnoConst.PROJECT_TYPE_ANNOTATION, prio = 100)
public class AnnotationPage
    extends AnnotationPageBase
{
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationPage.class);

    private static final long serialVersionUID = 1378872465851908515L;

    private @SpringBean DocumentService documentService;
    private @SpringBean ProjectService projectService;
    private @SpringBean ConstraintsService constraintsService;
    private @SpringBean SettingsService settingsService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean UserDao userRepository;
    private @SpringBean AnnotationEditorRegistry editorRegistry;

    private @SpringBean AnnotationEditorExtensionRegistry extensionRegistry;
    private NumberTextField<Integer> gotoPageTextField;
    
    private long currentprojectId;

    // Open the dialog window on first load
    private boolean showOpenDocumentSelectionDialog = true;
    
    private OpenDocumentDialog openDocumentsModal;
    private AnnotationPreferencesDialog preferencesModal;
    private ExportDocumentDialog exportDialog;
    private GuidelinesDialog guidelinesDialog;

    private FinishImage finishDocumentIcon;
    private ConfirmationDialog finishDocumentDialog;
    private LambdaAjaxLink finishDocumentLink;
    
    private AnnotationEditorBase annotationEditor;
    private AnnotationDetailEditorPanel detailEditor;    

    public AnnotationPage()
    {
        super();
        LOG.debug("Setting up annotation page without parameters");
        commonInit();
    }

    public AnnotationPage(final PageParameters aPageParameters)
    {
        super(aPageParameters);
        LOG.debug("Setting up annotation page with parameters: {}", aPageParameters);
        
        commonInit();

        getModelObject().setUser(userRepository.getCurrentUser());

        // Project has been specified when the page was opened
        Project project = null;
        StringValue projectParameter = aPageParameters.get(PAGE_PARAM_PROJECT_ID);
        try {
            project = getProjectFromParameters(projectParameter);
        }
        catch (NoResultException e) {
            error("Project [" + projectParameter + "] does not exist");
            return;
        }
        
        // Document has been specified when the page was opened
        SourceDocument document = null;
        StringValue documentParameter = aPageParameters.get(PAGE_PARAM_DOCUMENT_ID);
        if (project != null) {
            try {
                document = getDocumentFromParameters(project, documentParameter);
            }
            catch (NoResultException e) {
                error("Document [" + documentParameter + "] does not exist in project ["
                        + project.getId() + "]");
            }
        }
        
        handleParameters(null, project, document, true);
    }
    
    private void commonInit()
    {
        add(new UrlParametersReceivingBehavior()
        {
            private static final long serialVersionUID = -3860933016636718816L;

            @Override
            protected void onParameterArrival(IRequestParameters aRequestParameters,
                    AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                
                // Project has been specified when the page was opened
                Project project = null;
                StringValue projectParameter = aRequestParameters
                        .getParameterValue(PAGE_PARAM_PROJECT_ID);
                try {
                    project = getProjectFromParameters(projectParameter);
                }
                catch (NoResultException e) {
                    error("Project [" + projectParameter + "] does not exist");
                    return;
                }
                
                // Document has been specified when the page was opened
                SourceDocument document = null;
                StringValue documentParameter = aRequestParameters
                        .getParameterValue(PAGE_PARAM_DOCUMENT_ID);
                if (project != null) {
                    try {
                        document = getDocumentFromParameters(project, documentParameter);
                    }
                    catch (NoResultException e) {
                        error("Document [" + documentParameter + "] does not exist in project ["
                                + project.getId() + "]");
                    }
                }

                handleParameters(aTarget, project, document, false);
            }
        });        
        
        setModel(Model.of(new AnnotatorStateImpl(Mode.ANNOTATION)));

        add(createLeftSidebar());
        
        add(detailEditor = createDetailEditor());
        
        add(annotationEditor = createAnnotationEditor());

        add(createDocumentInfoLabel());

        add(getOrCreatePositionInfoLabel());

        add(openDocumentsModal = new OpenDocumentDialog("openDocumentsModal", getModel(),
                getAllowedProjects())
        {
            private static final long serialVersionUID = 5474030848589262638L;

            @Override
            public void onDocumentSelected(AjaxRequestTarget aTarget)
            {
                // Reload the page using AJAX. This does not add the project/document ID to the URL,
                // but being AJAX it flickers less.
                actionLoadDocument(aTarget);
                
                // Load the document and add the project/document ID to the URL. This causes a full
                // page reload. No AJAX.
                // PageParameters pageParameters = new PageParameters();
                // pageParameters.set(PAGE_PARAM_PROJECT_ID, getModelObject().getProject().getId());
                // pageParameters.set(PAGE_PARAM_DOCUMENT_ID,
                // getModelObject().getDocument().getId());
                // setResponsePage(AnnotationPage.class, pageParameters);
            }
        });
        
        add(preferencesModal = new AnnotationPreferencesDialog("preferencesDialog", getModel()));
        preferencesModal.setOnChangeAction(this::actionCompletePreferencesChange);
        
        add(exportDialog = new ExportDocumentDialog("exportDialog", getModel()));

        add(guidelinesDialog = new GuidelinesDialog("guidelinesDialog", getModel()));

        Form<Void> gotoPageTextFieldForm = new Form<>("gotoPageTextFieldForm");
        gotoPageTextField = new NumberTextField<>("gotoPageText", Model.of(1), Integer.class);
        // FIXME minimum and maximum should be obtained from the annotator state
        gotoPageTextField.setMinimum(1); 
        gotoPageTextField.setOutputMarkupId(true); 
        gotoPageTextFieldForm.add(gotoPageTextField);
        gotoPageTextFieldForm.add(new LambdaAjaxSubmitLink("gotoPageLink", gotoPageTextFieldForm,
                this::actionGotoPage));
        add(gotoPageTextFieldForm);

        add(new LambdaAjaxLink("showOpenDocumentDialog", this::actionShowOpenDocumentDialog));

        add(new ActionBarLink("showPreferencesDialog", this::actionShowPreferencesDialog));
        
        add(new ActionBarLink("showGuidelinesDialog", guidelinesDialog::show));

        add(new ActionBarLink("showExportDialog", exportDialog::show)
                .onConfigure(_this -> {
                    AnnotatorState state = AnnotationPage.this.getModelObject();
                    _this.setVisible(state.getProject() != null
                            && (isAdmin(state.getProject(), projectService,
                                    state.getUser()) || !state.getProject().isDisableExport()));
                }));

        add(new ActionBarLink("showPreviousDocument", t -> actionShowPreviousDocument(t))
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_up },
                        EventType.click)));

        add(new ActionBarLink("showNextDocument", t -> actionShowNextDocument(t))
                .add(new InputBehavior(new KeyType[] { KeyType.Shift, KeyType.Page_down },
                        EventType.click)));

        add(new ActionBarLink("showNext", t -> actionShowNextPage(t))
                .add(new InputBehavior(new KeyType[] { KeyType.Page_down }, EventType.click)));

        add(new ActionBarLink("showPrevious", t -> actionShowPreviousPage(t))
                .add(new InputBehavior(new KeyType[] { KeyType.Page_up }, EventType.click)));

        add(new ActionBarLink("showFirst", t -> actionShowFirstPage(t))
                .add(new InputBehavior(new KeyType[] { KeyType.Home }, EventType.click)));

        add(new ActionBarLink("showLast", t -> actionShowLastPage(t))
                .add(new InputBehavior(new KeyType[] { KeyType.End }, EventType.click)));

        add(new ActionBarLink("toggleScriptDirection", this::actionToggleScriptDirection));
        
        add(createOrGetResetDocumentDialog());
        add(createOrGetResetDocumentLink());
        
        add(finishDocumentDialog = new ConfirmationDialog("finishDocumentDialog",
                new StringResourceModel("FinishDocumentDialog.title", this, null),
                new StringResourceModel("FinishDocumentDialog.text", this, null)));
        add(finishDocumentLink = new LambdaAjaxLink("showFinishDocumentDialog",
                this::actionFinishDocument)
        {
            private static final long serialVersionUID = 874573384012299998L;

            @Override
            protected void onConfigure()
            {
                super.onConfigure();
                AnnotatorState state = AnnotationPage.this.getModelObject();
                setEnabled(state.getDocument() != null && !documentService
                        .isAnnotationFinished(state.getDocument(), state.getUser()));
            }
        });
        finishDocumentIcon = new FinishImage("finishImage", getModel());
        finishDocumentIcon.setOutputMarkupId(true);
        finishDocumentLink.add(finishDocumentIcon);
    }
    
    private IModel<List<DecoratedObject<Project>>> getAllowedProjects()
    {
        return LambdaModel.of(() -> {
            User user = userRepository.getCurrentUser();
            List<DecoratedObject<Project>> allowedProject = new ArrayList<>();
            for (Project project : projectService.listProjects()) {
                if (isAnnotator(project, projectService, user)
                        && WebAnnoConst.PROJECT_TYPE_ANNOTATION.equals(project.getMode())) {
                    allowedProject.add(DecoratedObject.of(project));
                }
            }
            return allowedProject;
        });
    }

    public NumberTextField<Integer> getGotoPageTextField()
    {
        return gotoPageTextField;
    }

    public void setGotoPageTextField(NumberTextField<Integer> aGotoPageTextField)
    {
        gotoPageTextField = aGotoPageTextField;
    }

    private DocumentNamePanel createDocumentInfoLabel()
    {
        return new DocumentNamePanel("documentNamePanel", getModel());
    }

    private AnnotationDetailEditorPanel createDetailEditor()
    {
        return new AnnotationDetailEditorPanel("annotationDetailEditorPanel", getModel())
        {
            private static final long serialVersionUID = 2857345299480098279L;

            @Override
            protected void onChange(AjaxRequestTarget aTarget)
            {
                aTarget.addChildren(getPage(), FeedbackPanel.class);
                aTarget.add(getOrCreatePositionInfoLabel());

                annotationEditor.requestRender(aTarget);
            }

            @Override
            protected void onAutoForward(AjaxRequestTarget aTarget)
            {
                annotationEditor.requestRender(aTarget);
            }
        };
    }
    
    private AnnotationEditorBase createAnnotationEditor()
    {
        String editorId = getModelObject().getPreferences().getEditor();
        
        AnnotationEditorFactory factory = editorRegistry.getEditorFactory(editorId);
        if (factory == null) {
            factory = editorRegistry.getDefaultEditorFactory();
        }

        return factory.create("embedder1", getModel(),
                detailEditor, this::getEditorCas);
    }

    private SidebarPanel createLeftSidebar()
    {
        return new SidebarPanel("leftSidebar", getModel(), detailEditor, () -> getEditorCas(),
                AnnotationPage.this);
    }

    @Override
    protected List<SourceDocument> getListOfDocs()
    {
        AnnotatorState state = getModelObject();
        return new ArrayList<>(documentService
                .listAnnotatableDocuments(state.getProject(), state.getUser()).keySet());
    }

    /**
     * for the first time, open the <b>open document dialog</b>
     */
    @Override
    public void renderHead(IHeaderResponse aResponse)
    {
        super.renderHead(aResponse);

        String jQueryString = "";
        if (showOpenDocumentSelectionDialog) {
            jQueryString += "jQuery('#showOpenDocumentDialog').trigger('click');";
            showOpenDocumentSelectionDialog = false;
        }
        aResponse.render(OnLoadHeaderItem.forScript(jQueryString));
        
        aResponse.render(CssContentHeaderItem.forCSS(
                        String.format(Locale.US, ".sidebarCell { flex-basis: %d%%; }",
                                getModelObject().getPreferences().getSidebarSize()),
                        "sidebar-width"));
    }

    @Override
    public JCas getEditorCas()
        throws IOException
    {
        AnnotatorState state = getModelObject();

        if (state.getDocument() == null) {
            throw new IllegalStateException("Please open a document first!");
        }
        
        SourceDocument aDocument = getModelObject().getDocument();

        AnnotationDocument annotationDocument = documentService.getAnnotationDocument(aDocument,
                state.getUser());

        // If there is no CAS yet for the annotation document, create one.
        return documentService.readAnnotationCas(annotationDocument);
    }

    private void actionShowOpenDocumentDialog(AjaxRequestTarget aTarget)
    {
        getModelObject().getSelection().clear();
        openDocumentsModal.show(aTarget);
    }

    private void actionShowPreferencesDialog(AjaxRequestTarget aTarget)
    {
        getModelObject().getSelection().clear();
        preferencesModal.show(aTarget);
    }

    private void actionGotoPage(AjaxRequestTarget aTarget, Form<?> aForm)
        throws Exception
    {
        AnnotatorState state = getModelObject();
        
        JCas jcas = getEditorCas();
        List<Sentence> sentences = new ArrayList<>(select(jcas, Sentence.class));
        int selectedSentence = gotoPageTextField.getModelObject();
        selectedSentence = Math.min(selectedSentence, sentences.size());
        gotoPageTextField.setModelObject(selectedSentence);
        
        state.setFirstVisibleUnit(sentences.get(selectedSentence - 1));
        state.setFocusUnitIndex(selectedSentence);        
        
        actionRefreshDocument(aTarget);
    }

    private void actionToggleScriptDirection(AjaxRequestTarget aTarget)
            throws Exception
    {
        getModelObject().toggleScriptDirection();
        annotationEditor.requestRender(aTarget);
    }
    
    private void actionCompletePreferencesChange(AjaxRequestTarget aTarget)
    {
        try {
            AnnotatorState state = getModelObject();
            
            JCas jCas = getEditorCas();
            
            // The number of visible sentences may have changed - let the state recalculate 
            // the visible sentences 
            Sentence sentence = selectByAddr(jCas, Sentence.class,
                    state.getFirstVisibleUnitAddress());
            state.setFirstVisibleUnit(sentence);
            
            AnnotationEditorBase newAnnotationEditor = createAnnotationEditor();
            annotationEditor.replaceWith(newAnnotationEditor);
            annotationEditor = newAnnotationEditor;
            
            // Reload all AJAX-enabled children of the page but not the page itself!
            forEach(child ->  {
                if (child.getOutputMarkupId()) {
                    aTarget.add(child);
                }
            });
        }
        catch (Exception e) {
            LOG.info("Error reading CAS " + e.getMessage());
            error("Error reading CAS " + e.getMessage());
        }
    }
    
    private void actionFinishDocument(AjaxRequestTarget aTarget)
    {
        finishDocumentDialog.setConfirmAction((aCallbackTarget) -> {
            ensureRequiredFeatureValuesSet(aCallbackTarget, getEditorCas());
            
            AnnotatorState state = getModelObject();
            AnnotationDocument annotationDocument = documentService.getAnnotationDocument(
                    state.getDocument(), state.getUser());

            annotationDocument.setState(AnnotationDocumentStateTransition.transition(
                    AnnotationDocumentStateTransition.
                    ANNOTATION_IN_PROGRESS_TO_ANNOTATION_FINISHED));
            
            // manually update state change!! No idea why it is not updated in the DB
            // without calling createAnnotationDocument(...)
            documentService.createAnnotationDocument(annotationDocument);
            
            aCallbackTarget.add(finishDocumentIcon);
            aCallbackTarget.add(finishDocumentLink);
            aCallbackTarget.add(detailEditor);
            aCallbackTarget.add(createOrGetResetDocumentLink());
        });
        finishDocumentDialog.show(aTarget);
    }

    @Override
    protected void actionLoadDocument(AjaxRequestTarget aTarget)
    {
        LOG.info("BEGIN LOAD_DOCUMENT_ACTION");
        
        AnnotatorState state = getModelObject();
        
        state.setUser(userRepository.getCurrentUser());

        AnnotationEditorBase newAnnotationEditor = createAnnotationEditor();
        annotationEditor.replaceWith(newAnnotationEditor);
        annotationEditor = newAnnotationEditor;
        
        try {
            // Check if there is an annotation document entry in the database. If there is none,
            // create one.
            AnnotationDocument annotationDocument = documentService
                    .createOrGetAnnotationDocument(state.getDocument(), state.getUser());

            // Read the CAS
            JCas editorCas = documentService.readAnnotationCas(annotationDocument);

            // Update the annotation document CAS
            documentService.upgradeCas(editorCas.getCas(), annotationDocument);

            // After creating an new CAS or upgrading the CAS, we need to save it
            documentService.writeAnnotationCas(editorCas.getCas().getJCas(), annotationDocument,
                    false);

            // (Re)initialize brat model after potential creating / upgrading CAS
            state.clearAllSelections();

            // Load constraints
            state.setConstraints(constraintsService.loadConstraints(state.getProject()));

            // Load user preferences
            PreferencesUtil.loadPreferences(state.getUser().getUsername(), settingsService,
                    projectService, annotationService, state, state.getMode());

            // Initialize the visible content
            state.setFirstVisibleUnit(WebAnnoCasUtil.getFirstSentence(editorCas));
            
            // if project is changed, reset some project specific settings
            if (currentprojectId != state.getProject().getId()) {
                state.clearRememberedFeatures();
            }

            currentprojectId = state.getProject().getId();

            LOG.debug("Configured BratAnnotatorModel for user [" + state.getUser() + "] f:["
                    + state.getFirstVisibleUnitIndex() + "] l:["
                    + state.getLastVisibleUnitIndex() + "] s:["
                    + state.getFocusUnitIndex() + "]");

            gotoPageTextField.setModelObject(1);

            // Reload all AJAX-enabled children of the page but not the page itself!
            if (aTarget != null) {
                forEach(child ->  {
                    if (child.getOutputMarkupId()) {
                        aTarget.add(child);
                    }
                });
            }

            // Update document state
            if (state.getDocument().getState().equals(SourceDocumentState.NEW)) {
                state.getDocument().setState(SourceDocumentStateTransition
                        .transition(SourceDocumentStateTransition.NEW_TO_ANNOTATION_IN_PROGRESS));
                documentService.createSourceDocument(state.getDocument());
            }
            
            // Reset the editor
            detailEditor.reset(aTarget);
            // Populate the layer dropdown box
            detailEditor.loadFeatureEditorModels(editorCas, aTarget);
            
            extensionRegistry.fireDocumentLoad(editorCas, getModelObject());

            // Update URL for current document
            if (aTarget != null) {
                UrlFragment fragment = new UrlFragment(aTarget);
                fragment.putParameter(PAGE_PARAM_PROJECT_ID,
                        state.getDocument().getProject().getId());
                fragment.putParameter(PAGE_PARAM_DOCUMENT_ID, state.getDocument().getId());
                // If we do not manually set editedFragment to false, then changine the URL 
                // manually or using the back/forward buttons in the browser only works every
                // second time. Might be a but in wicketstuff urlfragment... not sure.
                aTarget.appendJavaScript(
                        "try{if(window.UrlUtil){window.UrlUtil.editedFragment = false;}}catch(e){}");
            }
        }
        catch (Exception e) {
            handleException(aTarget, e);
        }

        LOG.info("END LOAD_DOCUMENT_ACTION");
    }
    
    @Override
    public void actionRefreshDocument(AjaxRequestTarget aTarget)
    {
        annotationEditor.requestRender(aTarget);
        gotoPageTextField.setModelObject(getModelObject().getFirstVisibleUnitIndex());
        aTarget.add(gotoPageTextField);
        aTarget.add(getOrCreatePositionInfoLabel());
    }

    private Project getProjectFromParameters(StringValue projectParam)
    {
        Project project = null;
        if (!projectParam.isEmpty()) {
            long projectId = projectParam.toLong();
            project = projectService.getProject(projectId);
        }
        return project;
    }

    private SourceDocument getDocumentFromParameters(Project aProject, StringValue documentParam)
    {
        SourceDocument document = null;
        if (!documentParam.isEmpty()) {
            long documentId = documentParam.toLong();
            document = documentService.getSourceDocument(aProject.getId(), documentId);
        }
        return document;
    }
    
    private void handleParameters(AjaxRequestTarget aTarget, Project aProject,
            SourceDocument aDocument, boolean aInitial)
    {
        if (aProject == null || aDocument == null) {
            return;
        }
        
        // If there is no change in the current document, then there is nothing to do. Mind
        // that document IDs are globally unique and a change in project does not happen unless
        // there is also a document change.
        if (aDocument.equals(getModelObject().getDocument())) {
            return;
        }
        
        // Check access to project
        if (aProject != null
                && !isAnnotator(aProject, projectService, getModelObject().getUser())) {
            error("You have no permission to access project [" + aProject.getId() + "]");
            return;
        }
        
        // Check if document is locked for the user
        if (aProject != null && aDocument != null && documentService
                .existsAnnotationDocument(aDocument, getModelObject().getUser())) {
            AnnotationDocument adoc = documentService.getAnnotationDocument(aDocument,
                    getModelObject().getUser());
            if (AnnotationDocumentState.IGNORE.equals(adoc.getState())) {
                error("Document [" + aDocument.getId() + "] in project [" + aProject.getId()
                        + "] is locked for user [" + getModelObject().getUser().getUsername()
                        + "]");
                return;
            }
        }

        if (aProject != null) {
            getModelObject().setProject(aProject);
            if (aInitial) {
                getModelObject().setProjectLocked(true);
                showOpenDocumentSelectionDialog = true;
            }
        }
        
        if (aDocument != null) {
            getModelObject().setDocument(aDocument, getListOfDocs());
            if (aInitial) {
                showOpenDocumentSelectionDialog = false;
            }
            actionLoadDocument(aTarget);
        }
    }

    
    @MenuItemCondition
    public static boolean menuItemCondition(ProjectService aRepo, UserDao aUserRepo)
    {
        User user = aUserRepo.getCurrentUser();
        return annotationEnabeled(aRepo, user, WebAnnoConst.PROJECT_TYPE_ANNOTATION);
    }
}
