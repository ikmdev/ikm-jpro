/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.komet.app;

import com.jpro.webapi.WebAPI;
import de.jangassen.MenuToolkit;
import de.jangassen.model.AppearanceMode;
import dev.ikm.komet.amplify.events.AmplifyTopics;
import dev.ikm.komet.amplify.events.CreateJournalEvent;
import dev.ikm.komet.amplify.events.JournalTileEvent;
import dev.ikm.komet.amplify.export.ArtifactExportController2;
import dev.ikm.komet.amplify.export.ExportDatasetController;
import dev.ikm.komet.amplify.export.ExportDatasetViewFactory;
import dev.ikm.komet.amplify.journal.JournalController;
import dev.ikm.komet.amplify.journal.JournalViewFactory;
import dev.ikm.komet.amplify.landingpage.LandingPageController;
import dev.ikm.komet.amplify.landingpage.LandingPageViewFactory;
import dev.ikm.komet.framework.KometNodeFactory;
import dev.ikm.komet.framework.ScreenInfo;
import dev.ikm.komet.framework.events.EvtBus;
import dev.ikm.komet.framework.events.EvtBusFactory;
import dev.ikm.komet.framework.events.Subscriber;
import dev.ikm.komet.framework.graphics.Icon;
import dev.ikm.komet.framework.graphics.LoadFonts;
import dev.ikm.komet.framework.preferences.KometPreferencesStage;
import dev.ikm.komet.framework.preferences.PrefX;
import dev.ikm.komet.framework.window.KometStageController;
import dev.ikm.komet.framework.window.MainWindowRecord;
import dev.ikm.komet.framework.window.WindowSettings;
import dev.ikm.komet.navigator.graph.GraphNavigatorNodeFactory;
import dev.ikm.komet.preferences.*;
import dev.ikm.komet.search.SearchNodeFactory;
import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TinkExecutor;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import one.jpro.platform.internal.util.PlatformUtils;
import one.jpro.platform.routing.Filters;
import one.jpro.platform.routing.Response;
import one.jpro.platform.routing.Route;
import one.jpro.platform.routing.RouteApp;
import org.carlfx.cognitive.loader.FXMLMvvmLoader;
import org.carlfx.cognitive.loader.JFXNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;

import static dev.ikm.komet.amplify.events.AmplifyTopics.JOURNAL_TOPIC;
import static dev.ikm.komet.amplify.events.JournalTileEvent.UPDATE_JOURNAL_TILE;
import static dev.ikm.komet.framework.window.WindowSettings.Keys.*;
import static dev.ikm.komet.preferences.JournalWindowPreferences.*;
import static dev.ikm.komet.preferences.JournalWindowSettings.*;
import static one.jpro.platform.routing.Route.get;

/**
 * Komet application running on JPro.
 */
public class JProApp extends RouteApp {

    public static final String KOMET_CSS_LOCATION = "dev/ikm/komet/framework/graphics/komet.css";
    public static final String AMPLIFY_CSS_LOCATION = "dev/ikm/komet/amplify/amplify-opt-2.css";

    public static final String SELECT_DATA_SOURCE_PAGE_PATH = "/page/selectDataSource";
    public static final String LANDING_PAGE_PATH = "/page/landing";
    public static final String JOURNAL_VIEW_PAGE_PATH = "/journalView";

    private static final Logger LOG = LoggerFactory.getLogger(JProApp.class);
    private static final String WORKING_DIR = System.getProperty("user.dir");
    private static final boolean IS_BROWSER = WebAPI.isBrowser();
    private static final boolean IS_DESKTOP = !IS_BROWSER && PlatformUtils.isDesktop();
    private static final boolean IS_MAC = !IS_BROWSER && PlatformUtils.isMac();

    private static Stage classicKometStage;
    private static KometPreferencesStage kometPreferencesStage;
    private final List<JournalController> journalControllersList = new ArrayList<>();
    private EvtBus amplifyEventBus;
    private static boolean firstRun = true;

    @Override
    public void init() {
        LOG.info("Starting Komet");
        LoadFonts.load();

        // get the instance of the event bus
        amplifyEventBus = EvtBusFactory.getInstance(EvtBus.class);
        Subscriber<CreateJournalEvent> detailsSubscriber = evt -> {
            String journalName = evt.getWindowSettingsObjectMap().getValue(JournalWindowSettings.JOURNAL_TITLE);
            // Inspects the existing journal windows to see if it is already open
            // So that we do not open duplicate journal windows
            journalControllersList.stream()
                    .filter(journalController -> journalController.getTitle().equals(journalName))
                    .findFirst()
                    .ifPresentOrElse(
                            JournalController::windowToFront, /* Window already launched now make window to the front (so user sees window) */
                            () -> launchJournalViewWindow(evt.getWindowSettingsObjectMap()) /* launch new Journal view window */
                    );
        };

        // subscribe to the topic
        amplifyEventBus.subscribe(AmplifyTopics.JOURNAL_TOPIC, CreateJournalEvent.class, detailsSubscriber);
    }

    @Override
    public Route createRoute() {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) ->
                AlertStreams.getRoot().dispatch(AlertObject.makeError(e)));

        getStage().setTitle("KOMET Startup");
        getScene().getStylesheets().addAll(getKometCssLocation(), getAmplifyCssLocation());

        getStage().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            ScreenInfo.mouseIsPressed(true);
            ScreenInfo.mouseWasDragged(false);
        });

        getStage().addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            ScreenInfo.mouseIsPressed(false);
            ScreenInfo.mouseIsDragging(false);
        });
        getStage().addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
            ScreenInfo.mouseIsDragging(true);
            ScreenInfo.mouseWasDragged(true);
        });

        getStage().showingProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue && firstRun) {
                LOG.info("Primary stage is showing");
                App.state.set(AppState.SELECT_DATA_SOURCE);
                App.state.addListener(this::appStateChangeListener);
                firstRun = false;
            }
        });

        return Route.empty()
                .and(get("/", request -> {
                    if (App.state.get().ordinal() == AppState.RUNNING.ordinal()) {
                        return Response.redirect(LANDING_PAGE_PATH);
                    } else {
                        return Response.redirect(SELECT_DATA_SOURCE_PAGE_PATH);
                    }
                }))
                .path("/page", Route.empty()
                        .and(get("/selectDataSource", request -> Response.node(selectDataSourcePage())))
                        .and(get("/landing", request -> Response.node(landingPage()))))
                .filter(Filters.FullscreenFilter(true)); // uses the whole browser window
    }

    @Override
    public void stop() {
        LOG.info("Stopping Komet");

        // close all journal windows
        if (IS_DESKTOP) {
            App.state.set(AppState.SHUTDOWN);
            journalControllersList.forEach(JournalController::close);
        }
    }

    private BorderPane selectDataSourcePage() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("SelectDataSource.fxml"));
        try {
            BorderPane rootNode = fxmlLoader.load();
            SelectDataSourceController selectDataSourceController = fxmlLoader.getController();
            return rootNode;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private MenuBar createMenuBar() {
        // Get the toolkit
        MenuToolkit menuToolkit = MenuToolkit.toolkit();
        Menu kometAppMenu = menuToolkit.createDefaultApplicationMenu("Komet");

        MenuItem prefsItem = new MenuItem("Komet preferences...");
        prefsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN));
//        prefsItem.setOnAction(event -> kometPreferencesStage.showPreferences());

        kometAppMenu.getItems().add(2, prefsItem);
        kometAppMenu.getItems().add(3, new SeparatorMenuItem());
        MenuItem appleQuit = kometAppMenu.getItems().getLast();
        appleQuit.setOnAction(event -> quit());

        // File Menu
        Menu fileMenu = new Menu("File");
        // Todo: import dataset
        MenuItem newItem = new MenuItem("Import Dataset");

        // Exporting data
        Menu exportMenu = new Menu("Export Dataset");
        MenuItem fhirMenuItem = new MenuItem("FHIR");
        fhirMenuItem.setOnAction(actionEvent -> openDatasetPage());
        exportMenu.getItems().addAll(createExportChangesetMenuItem(), fhirMenuItem);

        fileMenu.getItems().addAll(newItem, exportMenu, new SeparatorMenuItem(), menuToolkit.createCloseWindowMenuItem());

        // Edit
        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(createMenuItem("Undo"), createMenuItem("Redo"), new SeparatorMenuItem(),
                createMenuItem("Cut"), createMenuItem("Copy"), createMenuItem("Paste"), createMenuItem("Select All"));

        // View
        Menu viewMenu = new Menu("View");
        MenuItem classicKometPage = new MenuItem("Classic Komet");
        KeyCombination classicKometPageKeyCombo = new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN);
        classicKometPage.setOnAction(actionEvent -> {
            try {
                launchClassicKomet();
            } catch (IOException | BackingStoreException ex) {
                throw new RuntimeException(ex);
            }
        });
        classicKometPage.setAccelerator(classicKometPageKeyCombo);
        viewMenu.getItems().add(classicKometPage);

        // Window Menu
        Menu windowMenu = new Menu("Window");
        windowMenu.getItems().addAll(menuToolkit.createMinimizeMenuItem(), menuToolkit.createZoomMenuItem(), menuToolkit.createCycleWindowsItem(),
                new SeparatorMenuItem(), menuToolkit.createBringAllToFrontItem());

        // Help Menu
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().addAll(new MenuItem("Getting started"));

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(kometAppMenu, fileMenu, editMenu, viewMenu, windowMenu, helpMenu);

        if (IS_MAC) {
            menuToolkit.setApplicationMenu(kometAppMenu);
            menuToolkit.setAppearanceMode(AppearanceMode.AUTO);
            menuToolkit.setDockIconMenu(createDockMenu());
            menuToolkit.autoAddWindowMenuItems(windowMenu);

            menuToolkit.setGlobalMenuBar(menuBar);
            menuToolkit.setTrayMenu(createSampleMenu());
        }

        return menuBar;
    }

    private BorderPane landingPage() {
        FXMLLoader landingPageLoader = LandingPageViewFactory.createFXMLLoader();

        try {
            BorderPane amplifyLandingPageBorderPane = landingPageLoader.load();
            MenuBar menuBar = createMenuBar();
            if (IS_BROWSER) {
                amplifyLandingPageBorderPane.setTop(menuBar);
            } else if (!IS_MAC) { // if NOT on macOS
                createMenuOptions(amplifyLandingPageBorderPane);
            }
            LandingPageController landingPageController = landingPageLoader.getController();

            getStage().setMaximized(true);

            return amplifyLandingPageBorderPane;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * When a user selects the menu option View/New Journal a new Stage Window is launched.
     * This method will load a navigation panel to be a publisher and windows will be connected (subscribed) to the activity stream.
     *
     * @param journalWindowSettings if present will give the size and positioning of the journal window
     */
    private void launchJournalViewWindow(PrefX journalWindowSettings) {
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        KometPreferences windowPreferences = appPreferences.node(MAIN_KOMET_WINDOW);

        WindowSettings windowSettings = new WindowSettings(windowPreferences);

        Stage journalStageWindow = new Stage();
        FXMLLoader amplifyJournalLoader = JournalViewFactory.createFXMLLoader();
        JournalController journalController;
        try {
            BorderPane amplifyJournalBorderPane = amplifyJournalLoader.load();
            journalController = amplifyJournalLoader.getController();
            Scene sourceScene = new Scene(amplifyJournalBorderPane, 1200, 800);

            // Add Komet.css and amplify css
            sourceScene.getStylesheets().addAll(getKometCssLocation(), getAmplifyCssLocation());

            journalStageWindow.setScene(sourceScene);

            // if NOT on macOS
            if (!IS_MAC) {
                generateMsWindowsMenu(amplifyJournalBorderPane);
            }

            String journalName;
            if (journalWindowSettings != null) {
                // load journal specific window settings
                journalName = journalWindowSettings.getValue(JOURNAL_TITLE);
                journalStageWindow.setTitle(journalName);
                if (journalWindowSettings.getValue(JOURNAL_HEIGHT) != null) {
                    journalStageWindow.setHeight(journalWindowSettings.getValue(JOURNAL_HEIGHT));
                    journalStageWindow.setWidth(journalWindowSettings.getValue(JOURNAL_WIDTH));
                    journalStageWindow.setX(journalWindowSettings.getValue(JOURNAL_XPOS));
                    journalStageWindow.setY(journalWindowSettings.getValue(JOURNAL_YPOS));
                    journalController.recreateConceptWindows(journalWindowSettings);
                } else {
                    journalStageWindow.setMaximized(true);
                }
            }

            journalStageWindow.showingProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    System.out.println("Stage is hidden.");
                    saveJournalWindowsToPreferences();
                    // call shutdown method on the controller
                    journalController.shutdown();
                    journalControllersList.remove(journalController);
                    // enable Delete menu option
                    journalWindowSettings.setValue(CAN_DELETE, true);
                    amplifyEventBus.publish(JOURNAL_TOPIC, new JournalTileEvent(this, UPDATE_JOURNAL_TILE, journalWindowSettings));
                }
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Launch windows window pane inside journal view
        journalStageWindow.setOnShown(windowEvent -> {
            //TODO: Refactor factory constructor calls below to use ServiceLoader (make constructors private)
            KometNodeFactory navigatorNodeFactory = new GraphNavigatorNodeFactory();
            KometNodeFactory searchNodeFactory = new SearchNodeFactory();

            journalController.launchKometFactoryNodes(
                    journalWindowSettings.getValue(JOURNAL_TITLE),
                    windowSettings.getView(),
                    navigatorNodeFactory,
                    searchNodeFactory);
        });
        // disable the delete menu option for a Journal Card.
        journalWindowSettings.setValue(CAN_DELETE, false);
        amplifyEventBus.publish(JOURNAL_TOPIC, new JournalTileEvent(this, UPDATE_JOURNAL_TILE, journalWindowSettings));
        journalControllersList.add(journalController);

        if (IS_BROWSER) {
            getWebAPI().openStageAsTab(journalStageWindow);
        }
        journalStageWindow.show();
    }

    private void saveJournalWindowsToPreferences() {
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        KometPreferences journalPreferences = appPreferences.node(JOURNAL_WINDOW);

        // Non launched journal windows should be preserved.
        List<String> journalSubWindowFoldersFromPref = journalPreferences.getList(JOURNAL_NAMES);

        // launched (journal Controllers List) will overwrite existing window preferences.
        List<String> journalSubWindowFolders = new ArrayList<>(journalControllersList.size());
        for (JournalController controller : journalControllersList) {
            String journalSubWindowPrefFolder = controller.generateJournalDirNameBasedOnTitle();
            journalSubWindowFolders.add(journalSubWindowPrefFolder);

            KometPreferences journalSubWindowPreferences = appPreferences.node(JOURNAL_WINDOW +
                    File.separator + journalSubWindowPrefFolder);
            controller.saveConceptWindowPreferences(journalSubWindowPreferences);
            journalSubWindowPreferences.put(JOURNAL_TITLE, controller.getTitle());
            journalSubWindowPreferences.putDouble(JOURNAL_HEIGHT, controller.getHeight());
            journalSubWindowPreferences.putDouble(JOURNAL_WIDTH, controller.getWidth());
            journalSubWindowPreferences.putDouble(JOURNAL_XPOS, controller.getX());
            journalSubWindowPreferences.putDouble(JOURNAL_YPOS, controller.getY());
            journalSubWindowPreferences.put(JOURNAL_AUTHOR, LandingPageController.DEMO_AUTHOR);
            journalSubWindowPreferences.putLong(JOURNAL_LAST_EDIT, (LocalDateTime.now())
                    .atZone(ZoneId.systemDefault()).toEpochSecond());
            try {
                journalSubWindowPreferences.flush();
            } catch (BackingStoreException e) {
                throw new RuntimeException(e);
            }

        }

        // Make sure windows that are not summoned will not be deleted (not added to JOURNAL_NAMES)
        for (String x : journalSubWindowFolders) {
            if (!journalSubWindowFoldersFromPref.contains(x)) {
                journalSubWindowFoldersFromPref.add(x);
            }
        }
        journalPreferences.putList(JOURNAL_NAMES, journalSubWindowFoldersFromPref);

        try {
            journalPreferences.flush();
            appPreferences.flush();
            appPreferences.sync();
        } catch (BackingStoreException e) {
            LOG.error("error writing journal window flag to preferences", e);
        }
    }

    private void launchClassicKomet() throws IOException, BackingStoreException {
        if (IS_DESKTOP) {
            // If already launched bring to the front
            if (classicKometStage != null && classicKometStage.isShowing()) {
                classicKometStage.show();
                classicKometStage.toFront();
                return;
            }
        }
        classicKometStage = new Stage();

        //Starting up preferences and getting configurations
        Preferences.start();
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        boolean appInitialized = appPreferences.getBoolean(AppKeys.APP_INITIALIZED, false);
        if (appInitialized) {
            LOG.info("Restoring configuration preferences. ");
        } else {
            LOG.info("Creating new configuration preferences. ");
        }

        MainWindowRecord mainWindowRecord = MainWindowRecord.make();
        BorderPane kometRoot = mainWindowRecord.root();
        KometStageController controller = mainWindowRecord.controller();

        //Loading/setting the Komet screen
        Scene kometScene = new Scene(kometRoot, 1800, 1024);
        kometScene.getStylesheets().addAll(getKometCssLocation(), getAmplifyCssLocation());

        // if NOT on macOS
        if (!IS_MAC) {
            generateMsWindowsMenu(kometRoot);
        }

        classicKometStage.setScene(kometScene);
        classicKometStage.setTitle("Classic Komet");

        KometPreferences windowPreferences = appPreferences.node(JournalWindowPreferences.MAIN_KOMET_WINDOW);
        boolean mainWindowInitialized = windowPreferences.getBoolean(KometStageController.WindowKeys.WINDOW_INITIALIZED, false);
        controller.setup(windowPreferences);

        if (!mainWindowInitialized) {
            controller.setLeftTabs(App.makeDefaultLeftTabs(controller.windowView()), 0);
            controller.setCenterTabs(App.makeDefaultCenterTabs(controller.windowView()), 0);
            controller.setRightTabs(App.makeDefaultRightTabs(controller.windowView()), 1);
            windowPreferences.putBoolean(KometStageController.WindowKeys.WINDOW_INITIALIZED, true);
            appPreferences.putBoolean(AppKeys.APP_INITIALIZED, true);
        } else {
            // Restore nodes from preferences.
            windowPreferences.get(LEFT_TAB_PREFERENCES).ifPresent(leftTabPreferencesName ->
                    App.restoreTab(windowPreferences, leftTabPreferencesName, controller.windowView(),
                            controller::leftBorderPaneSetCenter));
            windowPreferences.get(CENTER_TAB_PREFERENCES).ifPresent(centerTabPreferencesName ->
                    App.restoreTab(windowPreferences, centerTabPreferencesName, controller.windowView(),
                            controller::centerBorderPaneSetCenter));
            windowPreferences.get(RIGHT_TAB_PREFERENCES).ifPresent(rightTabPreferencesName ->
                    App.restoreTab(windowPreferences, rightTabPreferencesName, controller.windowView(),
                            controller::rightBorderPaneSetCenter));
        }
        //Setting X and Y coordinates for location of the Komet stage
        classicKometStage.setX(controller.windowSettings().xLocationProperty().get());
        classicKometStage.setY(controller.windowSettings().yLocationProperty().get());
        classicKometStage.setHeight(controller.windowSettings().heightProperty().get());
        classicKometStage.setWidth(controller.windowSettings().widthProperty().get());
        classicKometStage.show();

        if (IS_BROWSER) {
            getWebAPI().openStageAsTab(classicKometStage);
        }

        JProApp.kometPreferencesStage = new KometPreferencesStage(controller.windowView().makeOverridableViewProperties());

        windowPreferences.sync();
        appPreferences.sync();
    }

    private String getKometCssLocation() {
        String frameworkDir = WORKING_DIR.replace("/application", "/framework/src/main/resources");
        String kometCssSourcePath = frameworkDir + "/" + KOMET_CSS_LOCATION;
        File kometCssFile = new File(kometCssSourcePath);
        return kometCssFile.toURI().toString();
    }

    private String getAmplifyCssLocation() {
        String amplifyDir = WORKING_DIR.replace("/application", "/amplify/src/main/resources");
        String amplifyCssSourcePath = amplifyDir + "/" + AMPLIFY_CSS_LOCATION;
        File amplifyCssFile = new File(amplifyCssSourcePath);
        return amplifyCssFile.toURI().toString();
    }

    private void handleEvent(ActionEvent actionEvent) {
        LOG.debug("clicked {}", actionEvent.getSource());  // NOSONAR
    }

    private void appStateChangeListener(ObservableValue<? extends AppState> observable,
                                        AppState oldValue, AppState newValue) {
        try {
            switch (newValue) {
                case SELECTED_DATA_SOURCE -> {
                    Platform.runLater(() -> App.state.set(AppState.LOADING_DATA_SOURCE));
                    TinkExecutor.threadPool().submit(new LoadDataSourceTask(App.state));
                }
                case RUNNING -> getSessionManager().gotoURL(LANDING_PAGE_PATH);
                case SHUTDOWN -> quit();
            }
        } catch (Throwable ex) {
            LOG.error("Error in appStateChangeListener", ex);
            Platform.exit();
        }
    }

    private void quit() {
        LOG.info("Quitting Komet");
        PrimitiveData.stop();
        Preferences.stop();
        Platform.exit();
    }

    private MenuItem createMenuItem(String title) {
        MenuItem menuItem = new MenuItem(title);
        menuItem.setOnAction(this::handleEvent);
        return menuItem;
    }

    private Menu createDockMenu() {
        Menu dockMenu = createSampleMenu();
        MenuItem open = new MenuItem("New Window");
        open.setGraphic(Icon.OPEN.makeIcon());
//        open.setOnAction(e -> createNewStage());
        dockMenu.getItems().addAll(new SeparatorMenuItem(), open);
        return dockMenu;
    }

    private Menu createSampleMenu() {
        Menu trayMenu = new Menu();
        trayMenu.setGraphic(Icon.TEMPORARY_FIX.makeIcon());
        MenuItem reload = new MenuItem("Reload");
        reload.setGraphic(Icon.SYNCHRONIZE_WITH_STREAM.makeIcon());
        reload.setOnAction(this::handleEvent);
        MenuItem print = new MenuItem("Print");
        print.setOnAction(this::handleEvent);

        Menu share = new Menu("Share");
        MenuItem mail = new MenuItem("Mail");
        mail.setOnAction(this::handleEvent);
        share.getItems().add(mail);

        trayMenu.getItems().addAll(reload, print, new SeparatorMenuItem(), share);
        return trayMenu;
    }

    public void createMenuOptions(BorderPane landingPageRoot) {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem about = new MenuItem("About");
        about.setOnAction(actionEvent -> showWindowsAboutScreen());
        fileMenu.getItems().add(about);

        MenuItem menuItemQuit = new MenuItem("Quit");
        KeyCombination quitKeyCombo = new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN);
        menuItemQuit.setOnAction(actionEvent -> quit());
        menuItemQuit.setAccelerator(quitKeyCombo);
        fileMenu.getItems().add(menuItemQuit);

        Menu viewMenu = new Menu("View");
        MenuItem classicKometMenuItem = createClassicKometMenuItem();
        viewMenu.getItems().add(classicKometMenuItem);

        Menu windowMenu = new Menu("Window");
        MenuItem minimizeWindow = new MenuItem("Minimize");
        KeyCombination minimizeKeyCombo = new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN);
        minimizeWindow.setOnAction(event -> {
            Stage obj = (Stage) landingPageRoot.getScene().getWindow();
            obj.setIconified(true);
        });
        minimizeWindow.setAccelerator(minimizeKeyCombo);
        windowMenu.getItems().add(minimizeWindow);

        menuBar.getMenus().add(fileMenu);
        menuBar.getMenus().add(viewMenu);
        menuBar.getMenus().add(windowMenu);
        Platform.runLater(() -> landingPageRoot.setTop(menuBar));
    }

    private MenuItem createClassicKometMenuItem() {
        MenuItem classicKometMenuItem = new MenuItem("Classic Komet");
        KeyCombination classicKometKeyCombo = new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN);
        classicKometMenuItem.setOnAction(actionEvent -> {
            try {
                launchClassicKomet();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (BackingStoreException e) {
                throw new RuntimeException(e);
            }
        });
        classicKometMenuItem.setAccelerator(classicKometKeyCombo);
        return classicKometMenuItem;
    }

    private void generateMsWindowsMenu(BorderPane kometRoot) {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");

        MenuItem about = new MenuItem("About");
        about.setOnAction(actionEvent -> showWindowsAboutScreen());
        fileMenu.getItems().add(about);

        // Todo: import dataset
        MenuItem importMenuItem = new MenuItem("Import Dataset");

        // Exporting data
        Menu exportMenu = new Menu("Export Dataset");
        MenuItem fhirMenuItem = new MenuItem("FHIR");
        fhirMenuItem.setOnAction(actionEvent -> openDatasetPage());
        exportMenu.getItems().addAll(createExportChangesetMenuItem(), fhirMenuItem);

        fileMenu.getItems().addAll(importMenuItem, exportMenu);

        MenuItem menuItemQuit = new MenuItem("Quit");
        KeyCombination quitKeyCombo = new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN);
        menuItemQuit.setOnAction(actionEvent -> quit());
        menuItemQuit.setAccelerator(quitKeyCombo);
        fileMenu.getItems().add(menuItemQuit);

        Menu editMenu = new Menu("Edit");
        MenuItem landingPage = new MenuItem("Landing Page");
        KeyCombination landingPageKeyCombo = new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN);
//        landingPage.setOnAction(actionEvent -> launchLandingPage());
        landingPage.setAccelerator(landingPageKeyCombo);
        editMenu.getItems().add(landingPage);

        Menu windowMenu = new Menu("Window");
        MenuItem minimizeWindow = new MenuItem("Minimize");
        KeyCombination minimizeKeyCombo = new KeyCodeCombination(KeyCode.M, KeyCombination.CONTROL_DOWN);
        minimizeWindow.setOnAction(event -> {
            Stage obj = (Stage) kometRoot.getScene().getWindow();
            obj.setIconified(true);
        });
        minimizeWindow.setAccelerator(minimizeKeyCombo);
        windowMenu.getItems().add(minimizeWindow);

        menuBar.getMenus().add(fileMenu);
        menuBar.getMenus().add(editMenu);
        menuBar.getMenus().add(windowMenu);
        Platform.runLater(() -> kometRoot.setTop(menuBar));
    }

    public void openDatasetPage() {
        KometPreferences appPreferences = KometPreferencesImpl.getConfigurationRootPreferences();
        KometPreferences windowPreferences = appPreferences.node(MAIN_KOMET_WINDOW);

        WindowSettings windowSettings = new WindowSettings(windowPreferences);
        Stage datasetStage = new Stage();
        FXMLLoader datasetPageLoader = ExportDatasetViewFactory.createFXMLLoaderForExportDataset();
        try {
            Pane datasetBorderPane = datasetPageLoader.load();
            ExportDatasetController datasetPageController = datasetPageLoader.getController();
            datasetPageController.setViewProperties(windowSettings.getView().makeOverridableViewProperties());
            Scene sourceScene = new Scene(datasetBorderPane, 550, 700);
            datasetStage.setScene(sourceScene);
            datasetStage.setTitle("Export Dataset");
            datasetStage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MenuItem createExportChangesetMenuItem() {
        MenuItem exportMenuItem = new MenuItem("_Export Changesets...");
        exportMenuItem.setOnAction(event -> {
            Stage stage = new Stage();
            JFXNode<Pane, ArtifactExportController2> jfxNode = FXMLMvvmLoader.make(
                    ArtifactExportController2.class.getResource("artifact-export2.fxml"));
            stage.setScene(new Scene(jfxNode.node()));
            stage.show();
        });
        return exportMenuItem;
    }

    public void showWindowsAboutScreen() {
        Stage aboutWindow = new Stage();
        Label kometLabel = new Label("Komet 1");
        kometLabel.setFont(new Font("Open Sans", 24));
        Label copyright = new Label("Copyright \u00a9 " + Year.now().getValue());
        copyright.setFont(new Font("Open Sans", 10));
        VBox container = new VBox(kometLabel, copyright);
        container.setAlignment(Pos.CENTER);
        Scene aboutScene = new Scene(container, 250, 100);
        aboutWindow.setScene(aboutScene);
        aboutWindow.setTitle("About Komet");
        aboutWindow.show();
    }

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "false");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Komet");

        // https://stackoverflow.com/questions/42598097/using-javafx-application-stop-method-over-shutdownhook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Starting shutdown hook");
            PrimitiveData.save();
            PrimitiveData.stop();
            LOG.info("Finished shutdown hook");
        }));

        launch(JProApp.class, args);
    }
}
