using System;
using System.Collections;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Linq.Expressions;
using System.Net.NetworkInformation;
using System.Reflection;
using System.Text;
using System.Windows.Forms;
using Microsoft.Win32;

namespace PLCDataAnalyzer
{
    internal static class Program
    {
        [STAThread] static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
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
            Text = "PLC Data Analyzer - TIA Portal Openness";
            Width = 1320; Height = 800; MinimumSize = new Size(1000, 620);
            Font = new Font("Segoe UI", 9F); StartPosition = FormStartPosition.CenterScreen;
            BuildUi();
            Load += (s, e) => Init();
        }

        void BuildUi()
        {
            var top = new Panel { Dock = DockStyle.Top, Height = 112, BackColor = Color.FromArgb(245,247,250) };
            Controls.Add(top);
            top.Controls.Add(new Label { Text = "PLC Data Analyzer", Font = new Font("Segoe UI Semibold", 18), Left = 14, Top = 12, AutoSize = true });
            top.Controls.Add(new Label { Text = "PLC IP", Left = 14, Top = 58, AutoSize = true });
            ip.SetBounds(14, 77, 145, 25); top.Controls.Add(ip);
            top.Controls.Add(new Label { Text = "Interfaccia PN/IE", Left = 174, Top = 58, AutoSize = true });
            nic.SetBounds(174, 77, 330, 25); top.Controls.Add(nic);
            var detect = Button("Rileva TIA V21", 520, 73, 125); detect.Click += (s,e) => Detect(); top.Controls.Add(detect);
            var open = Button("Apri progetto TIA", 655, 73, 145); open.Click += (s,e) => OpenProject(); top.Controls.Add(open);
            var upload = Button("UPLOAD PLC + VARIABILI", 810, 73, 205); upload.Click += (s,e) => Upload(); top.Controls.Add(upload);
            var csv = Button("Esporta CSV", 1025, 73, 110); csv.Click += (s,e) => ExportCsv(); top.Controls.Add(csv);
            status.Left = 520; status.Top = 20; top.Controls.Add(status);
            count.Left = 1030; count.Top = 20; top.Controls.Add(count);

            var filter = new Panel { Dock = DockStyle.Top, Height = 45 };
            Controls.Add(filter);
            filter.Controls.Add(new Label { Text = "Cerca", Left = 14, Top = 15, AutoSize = true });
            search.SetBounds(65, 10, 460, 25); search.TextChanged += (s,e) => ApplyFilter(); filter.Controls.Add(search);

            var split = new SplitContainer { Dock = DockStyle.Fill, Orientation = Orientation.Horizontal, SplitterDistance = 490 };
            Controls.Add(split);
            grid.Dock = DockStyle.Fill; grid.ReadOnly = true; grid.AllowUserToAddRows = false; grid.RowHeadersVisible = false;
            grid.AutoGenerateColumns = false; grid.SelectionMode = DataGridViewSelectionMode.FullRowSelect; grid.BackgroundColor = Color.White;
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Source", HeaderText="Origine", Width=100 });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Name", HeaderText="Variabile / oggetto", AutoSizeMode=DataGridViewAutoSizeColumnMode.Fill });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="DataType", HeaderText="Tipo", Width=150 });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Address", HeaderText="Indirizzo", Width=150 });
            grid.Columns.Add(new DataGridViewTextBoxColumn { DataPropertyName="Group", HeaderText="Gruppo", Width=210 });
            grid.DataSource = binding; split.Panel1.Controls.Add(grid);
            log.Dock = DockStyle.Fill; log.Multiline = true; log.ScrollBars = ScrollBars.Vertical; log.ReadOnly = true;
            log.Font = new Font("Consolas", 9); log.BackColor = Color.FromArgb(30,33,38); log.ForeColor = Color.Gainsboro;
            split.Panel2.Controls.Add(log);
        }

        static Button Button(string text,int x,int y,int w) { return new Button { Text=text, Left=x, Top=y, Width=w, Height=31 }; }
        void Init()
        {
            foreach (var n in NetworkInterface.GetAllNetworkInterfaces().Where(x => x.NetworkInterfaceType != NetworkInterfaceType.Loopback)) nic.Items.Add(n.Description);
            if (nic.Items.Count > 0) nic.SelectedIndex = 0;
            Detect();
        }
        void Detect()
        {
            try { var p=tia.Detect(); status.Text=p==null?"TIA V21 Openness: non trovato":"TIA V21 Openness: OK"; Log(p??"Openness V21 non rilevato"); }
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
            Run(()=>tia.UploadStation(ip.Text.Trim(),nic.Text.Trim(),Log));
        }
        void Run(Func<List<PlcVariable>> f)
        {
            try { Cursor=Cursors.WaitCursor; var r=f(); all.Clear(); all.AddRange(r); ApplyFilter(); Log("Trovati "+all.Count+" elementi"); }
            catch(Exception ex){ Error(ex); } finally { Cursor=Cursors.Default; }
        }
        void ApplyFilter()
        {
            var q=(search.Text??"").Trim(); IEnumerable<PlcVariable> r=all;
            if(q.Length>0) r=r.Where(v => (v.Name??"").IndexOf(q,StringComparison.OrdinalIgnoreCase)>=0 || (v.Group??"").IndexOf(q,StringComparison.OrdinalIgnoreCase)>=0);
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
        static string Q(string s){ return "\""+(s??"").Replace("\"","\"\"")+"\""; }
        void Log(string s){ log.AppendText(DateTime.Now.ToString("HH:mm:ss")+"  "+s+Environment.NewLine); }
        void Error(Exception ex){ Log("ERRORE: "+ex.Message); MessageBox.Show(this,ex.Message,"PLC Data Analyzer",MessageBoxButtons.OK,MessageBoxIcon.Error); }
    }

}
