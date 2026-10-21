using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Linq.Expressions;
using System.Reflection;
using System.Security;
using System.Security.Principal;
using System.Xml.Linq;
using Microsoft.Win32;

namespace PLCDataAnalyzer
{
    public sealed class TiaDiagnostics
    {
        public string ApiPath { get; set; }
        public string RegistryKey { get; set; }
        public bool IsOpennessGroupMember { get; set; }
    }

    public sealed class TiaService
    {
        string apiPath;
        readonly List<Assembly> assemblies = new List<Assembly>();
        object portal;

        public TiaDiagnostics GetDiagnostics()
        {
            string registryKey;
            var path = DetectInternal(out registryKey);
            return new TiaDiagnostics { ApiPath = path, RegistryKey = registryKey, IsOpennessGroupMember = IsMemberOfOpennessGroup() };
        }

        public string Detect() { string k; return DetectInternal(out k); }

        string DetectInternal(out string matchedKey)
        {
            matchedKey = null;
            if (!string.IsNullOrEmpty(apiPath) && Directory.Exists(apiPath)) return apiPath;
            var candidates = new List<string>();
            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (var hklm = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                    using (var pub = hklm.OpenSubKey(@"SOFTWARE\Siemens\Automation\Openness\21.0\PublicAPI"))
                    {
                        if (pub == null) continue;
                        foreach (var version in pub.GetSubKeyNames().OrderByDescending(x => x))
                        using (var net = pub.OpenSubKey(version + @"\net48"))
                        {
                            if (net == null) continue;
                            var baseDll = net.GetValue("Siemens.Engineering.Base") as string;
                            if (string.IsNullOrWhiteSpace(baseDll)) continue;
                            var dir = Path.GetDirectoryName(baseDll);
                            if (!string.IsNullOrWhiteSpace(dir)) candidates.Add(dir);
                            if (File.Exists(baseDll)) matchedKey = "HKLM(" + view + @")\SOFTWARE\Siemens\Automation\Openness\21.0\PublicAPI\" + version + @"\net48";
                        }
                    }
                }
                catch { }
            }
            candidates.Add(@"C:\Program Files\Siemens\Automation\Portal V21\PublicAPI\V21\net48");
            candidates.Add(@"C:\Program Files (x86)\Siemens\Automation\Portal V21\PublicAPI\V21\net48");
            apiPath = candidates.Distinct(StringComparer.OrdinalIgnoreCase)
                .FirstOrDefault(p => !string.IsNullOrWhiteSpace(p) && File.Exists(Path.Combine(p, "Siemens.Engineering.Base.dll")));
            return apiPath;
        }

        static bool IsMemberOfOpennessGroup()
        {
            try
            {
                var identity = WindowsIdentity.GetCurrent();
                foreach (var sid in identity.Groups)
                {
                    try
                    {
                        var account = (NTAccount)sid.Translate(typeof(NTAccount));
                        if (account.Value.EndsWith(@"\Siemens TIA Openness", StringComparison.OrdinalIgnoreCase) || account.Value.Equals("Siemens TIA Openness", StringComparison.OrdinalIgnoreCase)) return true;
                    }
                    catch { }
                }
            }
            catch { }
            return false;
        }

        public List<PlcVariable> ImportProject(string file, Action<string> log)
        {
            EnsurePortal(log); object project = null;
            try
            {
                log("Apro progetto: " + file);
                project = Call(Prop(portal, "Projects"), "Open", new FileInfo(Path.GetFullPath(file)));
                return Scan(project, log);
            }
            finally { Close(project); }
        }

        public List<PlcVariable> UploadStation(string ip, string interfaceName, string plcPassword, Action<string> log)
        {
            EnsurePortal(log); object project = null;
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PLCDataAnalyzer", "Uploads");
            Directory.CreateDirectory(dir);
            try
            {
                project = Call(Prop(portal, "Projects"), "Create", new DirectoryInfo(dir), "Upload_" + DateTime.Now.ToString("yyyyMMdd_HHmmss"));
                var providerType = FindType("StationUploadProvider");
                if (providerType == null) throw new InvalidOperationException("StationUploadProvider non trovato nelle librerie TIA V21.");
                var provider = GetService(project, providerType);
                if (provider == null) throw new InvalidOperationException("StationUploadProvider non disponibile per il progetto creato.");
                var configuration = Prop(provider, "Configuration");
                var mode = Call(Prop(configuration, "Modes"), "Find", "PN/IE");
                if (mode == null) throw new InvalidOperationException("Modalità PN/IE non disponibile in TIA Portal.");
                var pcs = Prop(mode, "PcInterfaces");
                var pc = SelectPcInterface(pcs, interfaceName, ip, log);
                if (pc == null) throw new InvalidOperationException("Non trovo un'interfaccia PN/IE adatta. Guarda nel log l'elenco delle interfacce viste da TIA Portal.");
                log("Interfaccia TIA selezionata: " + Convert.ToString(Prop(pc, "Name")));
                var address = Call(Prop(pc, "Addresses"), "Create", ip);
                log("Avvio Station Upload da " + ip + "...");

                var method = provider.GetType().GetMethods().Where(x => x.Name == "StationUpload")
                    .OrderBy(x => x.GetParameters().Length).FirstOrDefault(x => x.GetParameters().Length == 2 || x.GetParameters().Length == 3);
                if (method == null) throw new MissingMethodException(provider.GetType().FullName, "StationUpload");
                var ps = method.GetParameters(); var args = new object[ps.Length];
                args[0] = address; args[1] = MakeUploadCallback(ps[1].ParameterType, log, plcPassword);
                if (ps.Length == 3) args[2] = ps[2].ParameterType.IsEnum ? Enum.ToObject(ps[2].ParameterType, 0) : Type.Missing;
                object result;
                try { result = method.Invoke(provider, args); }
                catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
                log("Upload state: " + Convert.ToString(Prop(result, "State")));
                LogUploadMessages(result, log);
                if (Prop(result, "UploadedStation") == null) throw new InvalidOperationException("TIA non ha restituito la stazione caricata. Controlla messaggi, password/protezione CPU e interfaccia Ethernet.");
                TryCall(project, "Save");
                log("Upload terminato. Analisi di tag, DB e UDT...");
                return Scan(project, log);
            }
            finally { Close(project); }
        }

        object SelectPcInterface(object pcs, string requested, string ip, Action<string> log)
        {
            var list = Items(pcs).ToList();
            foreach (var item in list) log("TIA PN/IE interface: " + Convert.ToString(Prop(item, "Name")));
            object selected = null;
            if (!string.IsNullOrWhiteSpace(requested))
            {
                selected = list.FirstOrDefault(x => string.Equals(Convert.ToString(Prop(x, "Name")), requested, StringComparison.OrdinalIgnoreCase));
                if (selected == null)
                {
                    var r = Normalize(requested);
                    selected = list.FirstOrDefault(x => Normalize(Convert.ToString(Prop(x, "Name"))).Contains(r) || r.Contains(Normalize(Convert.ToString(Prop(x, "Name")))));
                }
            }
            foreach (var item in list)
            {
                try
                {
                    var devices = Call(item, "GetAccessibleDevices");
                    foreach (var dev in Items(devices))
                    {
                        var addr = Convert.ToString(Prop(dev, "Address"));
                        log("Accessible device: " + addr + "  " + Convert.ToString(Prop(dev, "Name")));
                        if (string.Equals(addr, ip, StringComparison.OrdinalIgnoreCase)) return item;
                    }
                }
                catch (Exception ex) { log("Scan accessible devices non disponibile su " + Convert.ToString(Prop(item, "Name")) + ": " + ex.Message); }
            }
            return selected;
        }

        static string Normalize(string s)
        {
            if (string.IsNullOrWhiteSpace(s)) return "";
            return new string(s.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());
        }

        Delegate MakeUploadCallback(Type delegateType, Action<string> log, string password)
        {
            var invoke = delegateType.GetMethod("Invoke");
            var pars = invoke.GetParameters().Select(x => Expression.Parameter(x.ParameterType, x.Name)).ToArray();
            if (pars.Length != 1) throw new InvalidOperationException("Firma callback upload inattesa: " + delegateType.FullName);
            var handler = typeof(TiaService).GetMethod(nameof(HandleUploadConfiguration), BindingFlags.Instance | BindingFlags.NonPublic);
            var body = Expression.Call(Expression.Constant(this), handler, Expression.Convert(pars[0], typeof(object)), Expression.Constant(log), Expression.Constant(password, typeof(string)));
            return Expression.Lambda(delegateType, body, pars).Compile();
        }

        void HandleUploadConfiguration(object cfg, Action<string> log, string password)
        {
            if (cfg == null) return;
            var type = cfg.GetType(); var name = type.Name; var message = Convert.ToString(Prop(cfg, "Message"));
            log("Upload callback: " + name + (string.IsNullOrWhiteSpace(message) ? "" : " - " + message));
            var setPassword = type.GetMethods(BindingFlags.Instance | BindingFlags.Public).FirstOrDefault(m => m.Name == "SetPassword" && m.GetParameters().Length == 1);
            if (setPassword != null)
            {
                if (string.IsNullOrEmpty(password)) throw new InvalidOperationException("Il PLC richiede una password di accesso in lettura. Inseriscila nel campo Password PLC e riprova.");
                var secure = new SecureString(); foreach (var c in password) secure.AppendChar(c); secure.MakeReadOnly();
                setPassword.Invoke(cfg, new object[] { secure }); log("Password PLC fornita al callback TIA."); return;
            }
            var currentSelection = type.GetProperty("CurrentSelection", BindingFlags.Instance | BindingFlags.Public);
            if (currentSelection != null && currentSelection.CanWrite && currentSelection.PropertyType.IsEnum)
            {
                var names = Enum.GetNames(currentSelection.PropertyType);
                var preferred = names.FirstOrDefault(n => string.Equals(n, "TryUpload", StringComparison.OrdinalIgnoreCase))
                             ?? names.FirstOrDefault(n => n.IndexOf("Try", StringComparison.OrdinalIgnoreCase) >= 0)
                             ?? names.FirstOrDefault(n => !string.Equals(n, "NoAction", StringComparison.OrdinalIgnoreCase));
                if (preferred != null) { currentSelection.SetValue(cfg, Enum.Parse(currentSelection.PropertyType, preferred), null); log("Scelta upload automatica: " + preferred); }
            }
        }

        void LogUploadMessages(object result, Action<string> log)
        {
            var messages = Prop(result, "Messages") ?? Prop(result, "Message");
            if (messages != null) WalkMessage(messages, 0, log);
        }
        void WalkMessage(object obj, int level, Action<string> log)
        {
            foreach (var m in Items(obj))
            {
                var text = Convert.ToString(Prop(m, "Message"));
                if (!string.IsNullOrWhiteSpace(text)) log(new string(' ', level * 2) + text);
                var children = Prop(m, "Messages"); if (children != null) WalkMessage(children, level + 1, log);
            }
        }

        void EnsurePortal(Action<string> log)
        {
            if (Detect() == null) throw new DirectoryNotFoundException("TIA Portal V21 Openness non trovato. Atteso Siemens.Engineering.Base.dll in PublicAPI\\V21\\net48.");
            if (!IsMemberOfOpennessGroup()) throw new UnauthorizedAccessException("L'utente Windows non risulta nel gruppo locale 'Siemens TIA Openness'. Aggiungilo al gruppo, poi fai logout/login completo.");
            if (portal != null) return;
            AppDomain.CurrentDomain.AssemblyResolve -= OnResolve; AppDomain.CurrentDomain.AssemblyResolve += OnResolve;
            Load("Siemens.Engineering.Base.dll"); Load("Siemens.Engineering.Step7.dll");
            var pt = FindType("TiaPortal"); var mt = FindType("TiaPortalMode");
            if (pt == null || mt == null) throw new TypeLoadException("API TIA V21 non caricata correttamente.");
            log("Avvio TIA Portal V21 tramite Openness..."); log("Se compare il firewall TIA Openness, autorizza PLCDataAnalyzer.exe.");
            try { portal = Activator.CreateInstance(pt, new[] { Enum.Parse(mt, "WithUserInterface") }); }
            catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
            log("TIA Portal V21 collegato.");
        }

        Assembly OnResolve(object sender, ResolveEventArgs e)
        {
            try
            {
                var requested = new AssemblyName(e.Name);
                if (!requested.Name.StartsWith("Siemens.Engineering.", StringComparison.OrdinalIgnoreCase)) return null;
                var f = Path.Combine(apiPath, requested.Name + ".dll"); return File.Exists(f) ? Assembly.LoadFrom(f) : null;
            }
            catch { return null; }
        }
        void Load(string name)
        {
            var f = Path.Combine(apiPath, name); if (!File.Exists(f)) return;
            var a = Assembly.LoadFrom(f); if (!assemblies.Any(x => string.Equals(x.FullName, a.FullName, StringComparison.OrdinalIgnoreCase))) assemblies.Add(a);
        }
        Type FindType(string name)
        {
            foreach (var f in Directory.GetFiles(apiPath, "Siemens.Engineering*.dll")) try { Load(Path.GetFileName(f)); } catch { }
            foreach (var a in assemblies) try { var t = a.GetTypes().FirstOrDefault(x => x.Name == name); if (t != null) return t; } catch { }
            return null;
        }

        List<PlcVariable> Scan(object project, Action<string> log)
        {
            var outList = new List<PlcVariable>(); var scType = FindType("SoftwareContainer");
            if (scType == null) throw new TypeLoadException("SoftwareContainer non trovato");
            foreach (var d in Items(Prop(project, "Devices")))
            {
                var dn = Convert.ToString(Prop(d, "Name"));
                WalkDevice(d, item =>
                {
                    object c = null; try { c = GetService(item, scType); } catch { } if (c == null) return;
                    var sw = Prop(c, "Software"); if (sw == null || sw.GetType().Name.IndexOf("PlcSoftware", StringComparison.OrdinalIgnoreCase) < 0) return;
                    log("PLC software: " + dn); ScanTags(sw, dn, outList); ScanBlocks(sw, dn, outList, log); ScanTypes(sw, dn, outList);
                });
            }
            return outList.GroupBy(x => (x.Source ?? "") + "|" + (x.Name ?? "") + "|" + (x.Group ?? "")).Select(g => g.First()).ToList();
        }

        void WalkDevice(object n, Action<object> f) { f(n); foreach (var x in Items(Prop(n, "DeviceItems"))) WalkDevice(x, f); }
        void ScanTags(object sw, string device, List<PlcVariable> r) { WalkTagGroup(Prop(sw, "TagTableGroup"), device, "", r); }
        void WalkTagGroup(object g, string device, string path, List<PlcVariable> r)
        {
            if (g == null) return;
            foreach (var t in Items(Prop(g, "TagTables")))
            {
                var tn = Convert.ToString(Prop(t, "Name"));
                foreach (var x in Items(Prop(t, "Tags"))) r.Add(new PlcVariable { Source="PLC Tag", Name=Convert.ToString(Prop(x,"Name")), DataType=Convert.ToString(Prop(x,"DataTypeName")), Address=Convert.ToString(Prop(x,"LogicalAddress")), Group=device+" / "+path+tn });
            }
            foreach (var x in Items(Prop(g, "Groups"))) { var n=Convert.ToString(Prop(x,"Name")); WalkTagGroup(x,device,path+n+"/",r); }
        }

        void ScanBlocks(object sw, string device, List<PlcVariable> r, Action<string> log) { WalkBlocks(Prop(sw,"BlockGroup"),device,"",r,log); }
        void WalkBlocks(object g, string device, string path, List<PlcVariable> r, Action<string> log)
        {
            if (g == null) return;
            foreach (var b in Items(Prop(g,"Blocks")))
            {
                var type=b.GetType().Name;
                if (type.IndexOf("DataBlock",StringComparison.OrdinalIgnoreCase)>=0)
                {
                    var dbName=Convert.ToString(Prop(b,"Name"));
                    r.Add(new PlcVariable{Source="DB",Name=dbName,DataType=type,Address="symbolic",Group=device+" / "+path});
                    TryExtractBlockMembers(b,dbName,device+" / "+path,r,log);
                }
            }
            foreach (var x in Items(Prop(g,"Groups"))) { var n=Convert.ToString(Prop(x,"Name")); WalkBlocks(x,device,path+n+"/",r,log); }
        }

        void TryExtractBlockMembers(object block, string dbName, string group, List<PlcVariable> r, Action<string> log)
        {
            string temp=null;
            try
            {
                var exportMethod=block.GetType().GetMethods(BindingFlags.Instance|BindingFlags.Public)
                    .FirstOrDefault(m=>m.Name=="Export"&&m.GetParameters().Length==2&&m.GetParameters()[0].ParameterType==typeof(FileInfo));
                if(exportMethod==null)return;
                var optType=exportMethod.GetParameters()[1].ParameterType; if(!optType.IsEnum)return;
                object withDefaults; try{withDefaults=Enum.Parse(optType,"WithDefaults");}catch{withDefaults=Enum.ToObject(optType,0);}
                temp=Path.Combine(Path.GetTempPath(),"PLCDataAnalyzer_"+Guid.NewGuid().ToString("N")+".xml");
                exportMethod.Invoke(block,new object[]{new FileInfo(temp),withDefaults}); if(!File.Exists(temp))return;
                var doc=XDocument.Load(temp); var members=doc.Descendants().Where(x=>x.Name.LocalName=="Member"&&x.Attribute("Name")!=null).ToList();
                foreach(var m in members)
                {
                    var chain=m.AncestorsAndSelf().Where(x=>x.Name.LocalName=="Member"&&x.Attribute("Name")!=null).Reverse().Select(x=>(string)x.Attribute("Name")).ToArray();
                    if(chain.Length==0)continue;
                    var memberPath=string.Join(".",chain); var dtype=(string)m.Attribute("Datatype")??(string)m.Attribute("DataType")??""; var offset=(string)m.Attribute("Offset")??"";
                    r.Add(new PlcVariable{Source="DB Var",Name=dbName+"."+memberPath,DataType=dtype,Address=string.IsNullOrWhiteSpace(offset)?dbName+"."+memberPath:dbName+"."+memberPath+"  [offset "+offset+"]",Group=group+dbName});
                }
                if(members.Count>0)log("DB "+dbName+": "+members.Count+" membri estratti");
            }
            catch(TargetInvocationException ex){log("DB "+dbName+": export membri non disponibile - "+((ex.InnerException??ex).Message));}
            catch(Exception ex){log("DB "+dbName+": export membri non disponibile - "+ex.Message);}
            finally{try{if(temp!=null&&File.Exists(temp))File.Delete(temp);}catch{}}
        }

        void ScanTypes(object sw,string device,List<PlcVariable> r){WalkTypes(Prop(sw,"TypeGroup"),device,"",r);}
        void WalkTypes(object g,string device,string path,List<PlcVariable> r)
        {
            if(g==null)return;
            foreach(var t in Items(Prop(g,"Types")))r.Add(new PlcVariable{Source="UDT",Name=Convert.ToString(Prop(t,"Name")),DataType=t.GetType().Name,Address="symbolic",Group=device+" / "+path});
            foreach(var x in Items(Prop(g,"Groups"))){var n=Convert.ToString(Prop(x,"Name"));WalkTypes(x,device,path+n+"/",r);}
        }

        object GetService(object target,Type service)
        {
            var m=target.GetType().GetMethods().FirstOrDefault(x=>x.Name=="GetService"&&x.IsGenericMethodDefinition&&x.GetParameters().Length==0);
            if(m!=null)return m.MakeGenericMethod(service).Invoke(target,null);
            foreach(var a in assemblies)
            {
                Type[] ts; try{ts=a.GetTypes();}catch{continue;}
                foreach(var t in ts)foreach(var x in t.GetMethods(BindingFlags.Static|BindingFlags.Public))
                {
                    if(x.Name!="GetService"||!x.IsGenericMethodDefinition||x.GetParameters().Length!=1)continue;
                    var p=x.GetParameters()[0].ParameterType; if(!p.IsAssignableFrom(target.GetType()))continue;
                    try{return x.MakeGenericMethod(service).Invoke(null,new[]{target});}catch{}
                }
            }
            return null;
        }

        static object Prop(object o,string n){if(o==null)return null;try{return o.GetType().GetProperty(n,BindingFlags.Instance|BindingFlags.Public)?.GetValue(o,null);}catch{return null;}}
        static object Call(object o,string n,params object[] a)
        {
            if(o==null)return null; Exception last=null;
            foreach(var m in o.GetType().GetMethods(BindingFlags.Instance|BindingFlags.Public).Where(x=>x.Name==n&&x.GetParameters().Length==a.Length))
            {
                try{return m.Invoke(o,a);}catch(TargetInvocationException ex){throw ex.InnerException??ex;}catch(Exception ex){last=ex;}
            }
            if(last!=null)throw last; throw new MissingMethodException(o.GetType().FullName,n);
        }
        static void TryCall(object o,string n){if(o==null)return;try{o.GetType().GetMethod(n,Type.EmptyTypes)?.Invoke(o,null);}catch{}}
        static void Close(object o){TryCall(o,"Close");}
        static IEnumerable<object> Items(object o){if(o is IEnumerable e)foreach(var x in e)if(x!=null)yield return x;}
    }
}
