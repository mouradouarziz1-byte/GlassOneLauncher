using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net.NetworkInformation;
using System.Security.Principal;
using System.Text;
using System.Windows.Forms;

namespace PLCDataAnalyzer
{
    internal static class Program
    {
        public static readonly string DataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PLCDataAnalyzer");
        public static readonly string LogFile = Path.Combine(DataDir, "PLCDataAnalyzer.log");

        [STAThread]
        static void Main()
        {
            try
            {
                Directory.CreateDirectory(DataDir);
                File.AppendAllText(LogFile, Environment.NewLine + "==== START " + DateTime.Now.ToString("s") + " ====" + Environment.NewLine);
                AppDomain.CurrentDomain.UnhandledException += (s, e) => WriteCrash(e.ExceptionObject as Exception);
                Application.ThreadException += (s, e) => WriteCrash(e.Exception);
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new MainForm());
            }
            catch (Exception ex)
            {
                WriteCrash(ex);
                MessageBox.Show("Errore avvio PLC Data Analyzer:\r\n\r\n" + ex + "\r\n\r\nLog: " + LogFile,
                    "PLC Data Analyzer v0.4", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        public static void WriteLog(string text)
        {
            try { Directory.CreateDirectory(DataDir); File.AppendAllText(LogFile, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff") + "  " + text + Environment.NewLine); }
            catch { }
        }
        static void WriteCrash(Exception ex) { WriteLog("FATAL: " + (ex == null ? "unknown" : ex.ToString())); }
    }

    public sealed class PlcVariable
    {
        public string Source { get; set; }
        public string Name { get; set; }
        public string DataType { get; set; }
        public string Address { get; set; }
        public string Group { get; set; }
    }

    public sealed class MainForm : Form
    {
        readonly TextBox ip = new TextBox { Text = "192.168.0.1" };
        readonly ComboBox nic = new ComboBox { DropDownStyle = ComboBoxStyle.DropDown };
        readonly TextBox password = new TextBox { UseSystemPasswordChar = true };
        readonly TextBox search = new TextBox();
        readonly Label status = new Label { AutoSize = true };
        readonly Label count = new Label { AutoSize = true };
        readonly DataGridView grid = new DataGridView();
        readonly TextBox log = new TextBox();
        readonly BindingSource binding = new BindingSource();
        readonly List<PlcVariable> all = new List<PlcVariable>();
        readonly TiaService tia = new TiaService();

        public MainForm()
        {
            Text = "PLC Data Analyzer v0.4 - TIA Portal Openness V21";
            Width = 1420; Height = 820; MinimumSize = new Size(1080, 650);
            Font = new Font("Segoe UI", 9F); StartPosition = FormStartPosition.CenterScreen;
            BuildUi();
            Load += (s, e) => Init();
        }

        void BuildUi()
        {
            var top = new Panel { Dock = DockStyle.Top, Height = 125, BackColor = Color.FromArgb(245,247,250) };
            Controls.Add(top);
            top.Controls.Add(new Label { Text = "PLC Data Analyzer", Font = new Font("Segoe UI Semibold", 18), Left = 14, Top = 10, AutoSize = true });
            top.Controls.Add(new Label { Text = "v0.4 x64", ForeColor = Color.DimGray, Left = 205, Top = 19, AutoSize = true });
            top.Controls.Add(new Label { Text = "PLC IP", Left = 14, Top = 61, AutoSize = true });
            ip.SetBounds(14, 80, 145, 25); top.Controls.Add(ip);
            top.Controls.Add(new Label { Text = "Interfaccia Ethernet", Left = 174, Top = 61, AutoSize = true });
            nic.SetBounds(174, 80, 315, 25); top.Controls.Add(nic);
            top.Controls.Add(new Label { Text = "Password PLC (se richiesta)", Left = 500, Top = 61, AutoSize = true });
            password.SetBounds(500, 80, 175, 25); top.Controls.Add(password);

            var detect = Button("Diagnostica TIA", 690, 76, 125); detect.Click += (s,e) => Detect(); top.Controls.Add(detect);
            var open = Button("Apri progetto TIA", 825, 76, 145); open.Click += (s,e) => OpenProject(); top.Controls.Add(open);
            var upload = Button("UPLOAD PLC + VARIABILI", 980, 76, 205); upload.Click += (s,e) => Upload(); top.Controls.Add(upload);
            var csv = Button("Esporta CSV", 1195, 76, 110); csv.Click += (s,e) => ExportCsv(); top.Controls.Add(csv);
            var logs = Button("Apri log", 1310, 76, 85); logs.Click += (s,e) => OpenLog(); top.Controls.Add(logs);
            status.Left = 500; status.Top = 20; top.Controls.Add(status);
            count.Left = 1195; count.Top = 20; top.Controls.Add(count);

            var filter = new Panel { Dock = DockStyle.Top, Height = 45 };
            Controls.Add(filter);
            filter.Controls.Add(new Label { Text = "Cerca", Left = 14, Top = 15, AutoSize = true });
            search.SetBounds(65, 10, 510, 25); search.TextChanged += (s,e) => ApplyFilter(); filter.Controls.Add(search);
            filter.Controls.Add(new Label { Text = "Primo avvio: TIA può chiedere di autorizzare l'app Openness. Scegli 'Sì' o 'Sì per tutte'.", Left = 600, Top = 15, AutoSize = true, ForeColor = Color.DarkOrange });

            var split = new SplitContainer { Dock = DockStyle.Fill, Orientation = Orientation.Horizontal, SplitterDistance = 500 };
            Controls.Add(split);
            grid.Dock = DockStyle.Fill; grid.ReadOnly = true; grid.AllowUserToAddRows = false; grid.RowHeadersVisible = false;
            grid.AutoGenerateColumns = false; grid.SelectionMode = DataGridViewSelectionMode.FullRowSelect; grid.BackgroundColor = Color.White;
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Source", HeaderText="Origine", Width=105 });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Name", HeaderText="Variabile / oggetto", AutoSizeMode=DataGridViewAutoSizeColumnMode.Fill });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="DataType", HeaderText="Tipo", Width=180 });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Address", HeaderText="Indirizzo / simbolico", Width=220 });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Group", HeaderText="Gruppo", Width=240 });
            grid.DataSource = binding; split.Panel1.Controls.Add(grid);
            log.Dock = DockStyle.Fill; log.Multiline = true; log.ScrollBars = ScrollBars.Vertical; log.ReadOnly = true;
            log.Font = new Font("Consolas", 9); log.BackColor = Color.FromArgb(30,33,38); log.ForeColor = Color.Gainsboro;
            split.Panel2.Controls.Add(log);
        }

        static Button Button(string text,int x,int y,int w) { return new Button { Text=text, Left=x, Top=y, Width=w, Height=31 }; }

        void Init()
        {
            Log("Avvio v0.4 - processo 64 bit: " + Environment.Is64BitProcess);
            foreach (var n in NetworkInterface.GetAllNetworkInterfaces().Where(x => x.NetworkInterfaceType != NetworkInterfaceType.Loopback))
                nic.Items.Add(n.Description);
            if (nic.Items.Count > 0) nic.SelectedIndex = 0;
            Detect();
        }

        void Detect()
        {
            try
            {
                var d = tia.GetDiagnostics();
                Log("Windows: " + Environment.OSVersion + " | x64 process=" + Environment.Is64BitProcess);
                Log("Utente: " + WindowsIdentity.GetCurrent().Name);
                Log("Gruppo Siemens TIA Openness: " + (d.IsOpennessGroupMember ? "OK" : "NON RILEVATO"));
                Log("TIA registry: " + (d.RegistryKey ?? "non trovato"));
                Log("Siemens.Engineering.Base.dll: " + (d.ApiPath ?? "non trovata"));
                status.Text = d.ApiPath == null ? "TIA V21 Openness: NON TROVATO" : "TIA V21 Openness: OK";
                status.ForeColor = d.ApiPath == null ? Color.Firebrick : Color.DarkGreen;
                if (!d.IsOpennessGroupMember)
                    Log("ATTENZIONE: aggiungi l'utente Windows al gruppo locale 'Siemens TIA Openness', poi fai logout/login.");
            }
            catch(Exception ex){ Error(ex); }
        }

        void OpenProject()
        {
            using(var d=new OpenFileDialog { Filter="TIA Portal V21 (*.ap21)|*.ap21|Tutti i file|*.*" })
                if(d.ShowDialog(this)==DialogResult.OK) Run(()=>tia.ImportProject(d.FileName,Log));
        }

        void Upload()
        {
            if(string.IsNullOrWhiteSpace(ip.Text)){ MessageBox.Show(this,"Inserisci l'IP PLC"); return; }
            Run(()=>tia.UploadStation(ip.Text.Trim(),nic.Text.Trim(),password.Text,Log));
        }

        void Run(Func<List<PlcVariable>> f)
        {
            try
            {
                Enabled = false; Cursor=Cursors.WaitCursor; Application.DoEvents();
                var r=f(); all.Clear(); all.AddRange(r); ApplyFilter(); Log("Trovati "+all.Count+" elementi");
            }
            catch(Exception ex){ Error(ex); }
            finally { Enabled = true; Cursor=Cursors.Default; Activate(); }
        }

        void ApplyFilter()
        {
            var q=(search.Text??"").Trim(); IEnumerable<PlcVariable> r=all;
            if(q.Length>0) r=r.Where(v => (v.Name??"").IndexOf(q,StringComparison.OrdinalIgnoreCase)>=0 || (v.Group??"").IndexOf(q,StringComparison.OrdinalIgnoreCase)>=0 || (v.DataType??"").IndexOf(q,StringComparison.OrdinalIgnoreCase)>=0);
            var list=r.OrderBy(v=>v.Name).ToList(); binding.DataSource=list; count.Text=list.Count+" elementi";
        }

        void ExportCsv()
        {
            if(all.Count==0) return;
            using(var d=new SaveFileDialog { Filter="CSV (*.csv)|*.csv", FileName="PLC_Variables.csv" })
            if(d.ShowDialog(this)==DialogResult.OK)
            using(var w=new StreamWriter(d.FileName,false,new UTF8Encoding(true)))
            { w.WriteLine("Source;Name;DataType;Address;Group"); foreach(var v in all) w.WriteLine(string.Join(";",Q(v.Source),Q(v.Name),Q(v.DataType),Q(v.Address),Q(v.Group))); }
        }

        void OpenLog()
        {
            try { Program.WriteLog("Apertura log richiesta"); Process.Start("notepad.exe", Program.LogFile); }
            catch(Exception ex) { Error(ex); }
        }
        static string Q(string s){ return "\""+(s??"").Replace("\"","\"\"")+"\""; }
        void Log(string s){ Program.WriteLog(s); log.AppendText(DateTime.Now.ToString("HH:mm:ss")+"  "+s+Environment.NewLine); Application.DoEvents(); }
        void Error(Exception ex){ Program.WriteLog("ERRORE: "+ex); Log("ERRORE: "+ex.Message); MessageBox.Show(this,ex.Message+"\r\n\r\nDettagli nel log:\r\n"+Program.LogFile,"PLC Data Analyzer v0.4",MessageBoxButtons.OK,MessageBoxIcon.Error); }
    }
}
