package com.sap.adt.mcp.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import com.sap.adt.mcp.sap.AdtRestClient;
import com.sap.adt.mcp.server.McpServer;
import com.sap.adt.mcp.tools.*;

/**
 * Eclipse view that manages the MCP Server and provides a terminal
 * for running Claude Code.
 */
public class McpServerView extends ViewPart {

    public static final String ID = "com.sap.adt.mcp.server.view";

    private static final int DEFAULT_PORT = 3000;

    private McpServer mcpServer;
    private AdtRestClient adtClient;
    private Process claudeProcess;
    private Thread outputThread;

    private Label statusLabel;
    private Button connectButton;
    private Button startButton;
    private Button launchClaudeButton;
    private StyledText outputText;

    // Connection details
    private String sapUrl;
    private String sapUser;
    private String sapPassword;
    private String sapClient;
    private String sapLanguage = "EN";

    @Override
    public void createPartControl(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        // Status bar
        Composite statusBar = new Composite(parent, SWT.NONE);
        statusBar.setLayout(new GridLayout(5, false));
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        statusLabel = new Label(statusBar, SWT.NONE);
        statusLabel.setText("SAP: Not Connected | MCP: Stopped");
        statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        connectButton = new Button(statusBar, SWT.PUSH);
        connectButton.setText("Connect SAP");
        connectButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                showConnectionDialog();
            }
        });

        startButton = new Button(statusBar, SWT.PUSH);
        startButton.setText("Start Server");
        startButton.setEnabled(false);
        startButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                toggleServer();
            }
        });

        launchClaudeButton = new Button(statusBar, SWT.PUSH);
        launchClaudeButton.setText("Launch Claude Code");
        launchClaudeButton.setEnabled(false);
        launchClaudeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                launchClaudeCode();
            }
        });

        Button clearButton = new Button(statusBar, SWT.PUSH);
        clearButton.setText("Clear");
        clearButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                outputText.setText("");
            }
        });

        // Output/terminal area
        outputText = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        outputText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        outputText.setEditable(false);
        outputText.setFont(parent.getDisplay().getSystemFont());

        appendOutput("ALÊ ADT ABAP - Servidor MCP\n");
        appendOutput("==================\n\n");
        appendOutput("Este plugin expõe ferramentas SAP ADT via protocolo MCP para o Claude Code.\n\n");
        appendOutput("1. Clique em 'Connect SAP' para inserir os detalhes de conexão.\n");
        appendOutput("2. Clique em 'Start Server' para iniciar o servidor MCP.\n");
        appendOutput("3. Clique em 'Launch Claude Code' para abrir o Claude no terminal.\n");
        appendOutput("   Ou execute manualmente: claude --mcp-server http://localhost:" + DEFAULT_PORT + "/mcp\n\n");

        // Initialize MCP server
        mcpServer = new McpServer(DEFAULT_PORT);
        mcpServer.setStatusListener((running, message) -> {
            Display.getDefault().asyncExec(() -> {
                updateStatusLabel();
                startButton.setText(running ? "Stop Server" : "Start Server");
                launchClaudeButton.setEnabled(running);
                appendOutput(message + "\n");
            });
        });
    }

    private void showConnectionDialog() {
        ConnectionDialog dialog = new ConnectionDialog(getSite().getShell());
        if (dialog.open() == Dialog.OK) {
            sapUrl = dialog.getUrl();
            sapUser = dialog.getUser();
            sapPassword = dialog.getPassword();
            sapClient = dialog.getSapClient();
            sapLanguage = dialog.getLanguage();

            // Save connection details (never password) for next time
            saveConnectionHistory();

            connectToSap();
        }
    }

    private void saveConnectionHistory() {
        try {
            org.eclipse.jface.preference.IPreferenceStore store =
                    com.sap.adt.mcp.Activator.getDefault().getPreferenceStore();

            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_URL, sapUrl);
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_USER, sapUser);
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_CLIENT, sapClient);
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_LANGUAGE, sapLanguage);

            // Add URL to history (keep last 10, semicolon-separated)
            String history = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_URL_HISTORY);
            java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
            urls.add(sapUrl); // most recent first
            if (history != null && !history.isEmpty()) {
                for (String u : history.split(";")) {
                    if (!u.trim().isEmpty()) urls.add(u.trim());
                }
            }
            // Keep max 10
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (String u : urls) {
                if (count++ >= 10) break;
                if (sb.length() > 0) sb.append(";");
                sb.append(u);
            }
            store.setValue(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_URL_HISTORY, sb.toString());
        } catch (Exception e) {
            // Preference store not available, ignore
        }
    }

    private void connectToSap() {
        appendOutput("Conectando ao SAP em " + sapUrl + "...\n");

        try {
            adtClient = new AdtRestClient(sapUrl, sapUser, sapPassword, sapClient, sapLanguage, true);
            adtClient.login();

            appendOutput("Conectado com sucesso ao SAP!\n");
            appendOutput("Registrando ferramentas SAP...\n");

            registerSapTools();

            appendOutput("Registradas " + mcpServer.getToolCount() + " ferramentas.\n\n");
            startButton.setEnabled(true);
            updateStatusLabel();

        } catch (Exception e) {
            appendOutput("ERRO: Falha ao conectar ao SAP: " + e.getMessage() + "\n");
            adtClient = null;
        }
    }

    private void registerSapTools() {
        // Lista única de tools, compartilhada com o entrypoint standalone (ToolRegistry).
        mcpServer.registerTools(ToolRegistry.createAll(adtClient));
    }

    private void updateStatusLabel() {
        String sapStatus = adtClient != null && adtClient.isLoggedIn() ? "Connected" : "Not Connected";
        String mcpStatus = mcpServer != null && mcpServer.isRunning() ? "Running" : "Stopped";
        statusLabel.setText("SAP: " + sapStatus + " | MCP: " + mcpStatus);
    }

    private void toggleServer() {
        if (mcpServer.isRunning()) {
            mcpServer.stop();
        } else {
            try {
                mcpServer.start();
                writeMcpConfig();
            } catch (IOException e) {
                appendOutput("ERRO: Falha ao iniciar o servidor: " + e.getMessage() + "\n");
            }
        }
    }

    private void writeMcpConfig() {
        String host = getServerHost();
        String adtUrl = "http://" + host + ":" + DEFAULT_PORT + "/mcp";
        String docsUrl = "https://mcp-sap-docs.marianzeis.de/mcp";
        
        appendOutput("\n");
        appendOutput("🚀 ALÊ ADT ABAP - Pronto para Uso!\n");
        appendOutput("===========================================================================\n");
        appendOutput("🤖 SYSTEM PROMPT (PARA A IA LER):\n");
        appendOutput("- Você está conectado ao SAP ADT.\n");
        appendOutput("- Para criar qualquer objeto ABAP (PROG, CLAS, INTF, FUGR/FF, DDLS, DCLS, DDLX, DDLA, TABL, STRU, DTEL, DOMA, MSAG, SRVD, SRVB, BDEF), use sap_create_object (alias abap_creation-create_object) diretamente -- ele já injeta o XML correto. Passe 'initialSource' para gravar a fonte inicial na mesma chamada.\n");
        appendOutput("- NUNCA crie ou dependa de uma classe Z auxiliar (classrun/cl_dd_ddl_handler_factory) para criar objetos. sap_create_object não depende disso.\n");
        appendOutput("- Fale sempre em Português.\n");
        appendOutput("===========================================================================\n\n");
        
        appendOutput("Cole este comando no Claude Code para registrar este plugin:\n");
        appendOutput("claude mcp add --transport http --scope user ale-adt " + adtUrl + "\n\n");

        if (!host.equals("localhost")) {
            appendOutput("NOTA: Usando IP " + host + " para compatibilidade com WSL.\n\n");
        }

        // Copy main command to clipboard
        String commands = "claude mcp add --transport http --scope user ale-adt " + adtUrl;
        try {
            org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
            org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
            clipboard.setContents(new Object[]{commands}, new org.eclipse.swt.dnd.Transfer[]{textTransfer});
            clipboard.dispose();
            appendOutput("Comando copiado para a área de transferência!\n\n");
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Get the host address for the MCP server URL.
     * On Windows, detect the host IP accessible from WSL instead of localhost.
     */
    private String getServerHost() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (!isWindows) {
            return "localhost";
        }

        // On Windows, find an IP that WSL can reach
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                // Look for the vEthernet (WSL) adapter or any non-loopback IPv4
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String name = ni.getDisplayName().toLowerCase();
                        // Prefer WSL vEthernet adapter
                        if (name.contains("wsl") || name.contains("vethernet")) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }

            // Fallback: return first non-loopback IPv4
            interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not detect host IP: " + e.getMessage());
        }

        return "localhost";
    }

    private void launchClaudeCode() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");

            // Determine the command to copy to clipboard
            String clipboardCmd = isWindows ? "wsl -- claude" : "claude";

            // Try to open Eclipse's built-in terminal view
            org.eclipse.ui.IWorkbenchPage page = getSite().getPage();

            try {
                org.eclipse.ui.IViewPart terminalView = page.showView(
                        "org.eclipse.tm.terminal.view.ui.TerminalsView",
                        null,
                        org.eclipse.ui.IWorkbenchPage.VIEW_ACTIVATE);

                if (terminalView != null) {
                    // Copy command to clipboard for easy pasting
                    org.eclipse.swt.dnd.Clipboard clipboard = new org.eclipse.swt.dnd.Clipboard(Display.getDefault());
                    org.eclipse.swt.dnd.TextTransfer textTransfer = org.eclipse.swt.dnd.TextTransfer.getInstance();
                    clipboard.setContents(new Object[]{clipboardCmd}, new org.eclipse.swt.dnd.Transfer[]{textTransfer});
                    clipboard.dispose();

                    appendOutput("Terminal do Eclipse aberto.\n");
                    appendOutput("Cole '" + clipboardCmd + "' (já está na área de transferência) para iniciar o Claude Code.\n");
                    if (isWindows) {
                        appendOutput("WSL é utilizado pois o Claude Code requer um ambiente Unix no Windows.\n");
                    }
                    return;
                }
            } catch (org.eclipse.ui.PartInitException e) {
                // TM Terminal not available, try fallback
            }

            // Fallback: Open external terminal
            appendOutput("Terminal do Eclipse indisponível. Abrindo terminal externo...\n");
            openExternalTerminal();

        } catch (Exception e) {
            appendOutput("ERRO: Falha ao abrir o terminal: " + e.getMessage() + "\n");
            appendOutput("Você pode executar manualmente: claude\n");
        }
    }

    private boolean isWslAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"wsl.exe", "--status"});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void openExternalTerminal() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        if (os.contains("mac")) {
            String script = "tell application \"Terminal\"\n"
                    + "    activate\n"
                    + "    do script \"claude\"\n"
                    + "end tell";
            pb = new ProcessBuilder("osascript", "-e", script);
        } else if (os.contains("win")) {
            if (isWslAvailable()) {
                // Try Windows Terminal with WSL first (modern Windows)
                if (isCommandAvailable("wt.exe")) {
                    pb = new ProcessBuilder("wt.exe", "-p", "Ubuntu", "--", "wsl", "--", "claude");
                    appendOutput("Abrindo Windows Terminal com WSL...\n");
                } else {
                    // Fall back to wsl.exe directly in a new cmd window
                    pb = new ProcessBuilder("cmd", "/c", "start", "wsl.exe", "--", "claude");
                    appendOutput("Abrindo terminal WSL...\n");
                }
            } else {
                appendOutput("AVISO: WSL não encontrado. Claude Code precisa de um ambiente Unix no Windows.\n");
                appendOutput("Instale o WSL: wsl --install\n");
                appendOutput("Em seguida, instale o Claude Code no WSL: npm install -g @anthropic-ai/claude-code\n");
                pb = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k",
                        "echo Claude Code necessita do WSL no Windows. Execute: wsl --install");
            }
        } else {
            // Linux: Try common terminals
            String[] terminals = {"gnome-terminal", "konsole", "xfce4-terminal", "xterm"};
            String terminal = null;
            for (String t : terminals) {
                if (isCommandAvailable(t)) {
                    terminal = t;
                    break;
                }
            }
            if (terminal != null) {
                if (terminal.equals("gnome-terminal")) {
                    pb = new ProcessBuilder(terminal, "--", "claude");
                } else {
                    pb = new ProcessBuilder(terminal, "-e", "claude");
                }
            } else {
                throw new IOException("Nenhum emulador de terminal encontrado.");
            }
        }

        pb.start();
        appendOutput("Claude Code iniciado em terminal externo.\n");
    }

    private boolean isCommandAvailable(String command) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            Process p;
            if (os.contains("win")) {
                p = Runtime.getRuntime().exec(new String[]{"where", command});
            } else {
                p = Runtime.getRuntime().exec(new String[]{"which", command});
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void appendOutput(String text) {
        if (outputText != null && !outputText.isDisposed()) {
            outputText.append(text);
            outputText.setTopIndex(outputText.getLineCount() - 1);
        }
    }

    @Override
    public void setFocus() {
        if (outputText != null && !outputText.isDisposed()) {
            outputText.setFocus();
        }
    }

    @Override
    public void dispose() {
        if (mcpServer != null && mcpServer.isRunning()) {
            mcpServer.stop();
        }
        if (claudeProcess != null && claudeProcess.isAlive()) {
            claudeProcess.destroy();
        }
        if (adtClient != null) {
            adtClient.logout();
        }
        super.dispose();
    }

    /**
     * Connection dialog for SAP system details.
     * Loads previous connection details from preferences (except password).
     */
    private class ConnectionDialog extends Dialog {
        private org.eclipse.swt.widgets.Combo urlCombo;
        private Text userText;
        private Text passwordText;
        private Text clientText;
        private Text languageText;

        private String url;
        private String user;
        private String password;
        private String client;
        private String language;

        protected ConnectionDialog(Shell parentShell) {
            super(parentShell);
        }

        @Override
        protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText("Conectar ao Sistema SAP");
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite container = (Composite) super.createDialogArea(parent);
            container.setLayout(new GridLayout(2, false));

            // Load saved values from preferences
            String savedUrl = "";
            String savedUser = "";
            String savedClient = "100";
            String savedLanguage = "PT";
            String[] urlHistory = new String[0];

            try {
                org.eclipse.jface.preference.IPreferenceStore store =
                        com.sap.adt.mcp.Activator.getDefault().getPreferenceStore();
                savedUrl = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_URL);
                savedUser = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_USER);
                savedClient = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_CLIENT);
                savedLanguage = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_LAST_LANGUAGE);
                String history = store.getString(com.sap.adt.mcp.preferences.PreferenceInitializer.PREF_URL_HISTORY);
                if (history != null && !history.isEmpty()) {
                    urlHistory = history.split(";");
                }
            } catch (Exception e) {
                // Preferences not available
            }

            // Use saved values, fall back to current session values, then defaults
            String defaultUrl = !savedUrl.isEmpty() ? savedUrl : (sapUrl != null ? sapUrl : "");
            String defaultUser = !savedUser.isEmpty() ? savedUser : (sapUser != null ? sapUser : "");
            String defaultClient = !savedClient.isEmpty() ? savedClient : (sapClient != null ? sapClient : "100");
            String defaultLanguage = !savedLanguage.isEmpty() ? savedLanguage : (sapLanguage != null ? sapLanguage : "PT");

            new Label(container, SWT.NONE).setText("URL SAP:");
            urlCombo = new org.eclipse.swt.widgets.Combo(container, SWT.BORDER);
            urlCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            // Populate URL dropdown with history
            for (String histUrl : urlHistory) {
                if (!histUrl.trim().isEmpty()) {
                    urlCombo.add(histUrl.trim());
                }
            }
            urlCombo.setText(defaultUrl);

            new Label(container, SWT.NONE).setText("Usuário:");
            userText = new Text(container, SWT.BORDER);
            userText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            userText.setText(defaultUser);

            new Label(container, SWT.NONE).setText("Senha:");
            passwordText = new Text(container, SWT.BORDER | SWT.PASSWORD);
            passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            new Label(container, SWT.NONE).setText("Mandante:");
            clientText = new Text(container, SWT.BORDER);
            clientText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            clientText.setText(defaultClient);

            new Label(container, SWT.NONE).setText("Idioma:");
            languageText = new Text(container, SWT.BORDER);
            languageText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            languageText.setText(defaultLanguage);

            // Focus password field since other fields are pre-filled
            container.getDisplay().asyncExec(() -> passwordText.setFocus());

            return container;
        }

        @Override
        protected void createButtonsForButtonBar(Composite parent) {
            createButton(parent, IDialogConstants.OK_ID, "Conectar", true);
            createButton(parent, IDialogConstants.CANCEL_ID, "Cancelar", false);
        }

        @Override
        protected void okPressed() {
            url = urlCombo.getText().trim();
            user = userText.getText().trim();
            password = passwordText.getText();
            client = clientText.getText().trim();
            language = languageText.getText().trim();
            super.okPressed();
        }

        public String getUrl() { return url; }
        public String getUser() { return user; }
        public String getPassword() { return password; }
        public String getSapClient() { return client; }
        public String getLanguage() { return language; }
    }
}
