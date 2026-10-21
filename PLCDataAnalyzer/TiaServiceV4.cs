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
        string registryPath;
        object portal;
        bool resolverInstalled;
        readonly List<Assembly> assemblies = new List<Assembly>();

        public TiaDiagnostics GetDiagnostics()
        {
            return new TiaDiagnostics
            {
                ApiPath = DetectInternal(),
                RegistryKey = registryPath,
                IsOpennessGroupMember = IsMemberOfOpennessGroup()
            };
        }

        public string Detect() { return DetectInternal(); }

        string DetectInternal()
        {
            if (!string.IsNullOrWhiteSpace(apiPath) && File.Exists(Path.Combine(apiPath, "Siemens.Engineering.Base.dll"))) return apiPath;
            registryPath = null;
            var candidates = new List<string>();

            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (var hklm = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                    using (var root = hklm.OpenSubKey(@"SOFTWARE\Siemens\Automation\Openness"))
                    {
                        if (root == null) continue;
                        foreach (var pv in root.GetSubKeyNames().Where(x => x.StartsWith("21", StringComparison.OrdinalIgnoreCase)).OrderByDescending(x => x))
                        using (var pub = root.OpenSubKey(pv + @"\PublicAPI"))
                        {
                            if (pub == null) continue;
                            foreach (var ev in pub.GetSubKeyNames().OrderByDescending(x => x))
                            using (var net = pub.OpenSubKey(ev + @"\net48"))
                            {
                                if (net == null) continue;
                                var baseDll = net.GetValue("Siemens.Engineering.Base") as string;
                                if (string.IsNullOrWhiteSpace(baseDll)) continue;
                                var dir = Path.GetDirectoryName(baseDll);
                                if (!string.IsNullOrWhiteSpace(dir)) candidates.Add(dir);
                                if (File.Exists(baseDll) && registryPath == null)
                                    registryPath = "HKLM(" + view + @")\SOFTWARE\Siemens\Automation\Openness\" + pv + @"\PublicAPI\" + ev + @"\net48";
                            }
                        }
                    }
                }
                catch { }
            }

            candidates.Add(@"C:\Program Files\Siemens\Automation\Portal V21\PublicAPI\V21\net48");
            apiPath = candidates.Distinct(StringComparer.OrdinalIgnoreCase)
                .FirstOrDefault(p => !string.IsNullOrWhiteSpace(p) && File.Exists(Path.Combine(p, "Siemens.Engineering.Base.dll")));
            return apiPath;
        }

        static bool IsMemberOfOpennessGroup()
        {
            try
            {
                var identity = WindowsIdentity.GetCurrent();
                if (identity == null || identity.Groups == null) return false;
                foreach (var sid in identity.Groups)
                {
                    try
                    {
                        var a = (NTAccount)sid.Translate(typeof(NTAccount));
                        if (a.Value.EndsWith(@"\Siemens TIA Openness", StringComparison.OrdinalIgnoreCase) || a.Value.Equals("Siemens TIA Openness", StringComparison.OrdinalIgnoreCase)) return true;
                    }
                    catch { }
                }
            }
            catch { }
            return false;
        }

        void EnsureResolver()
        {
            if (resolverInstalled) return;
            AppDomain.CurrentDomain.AssemblyResolve += (s, e) =>
            {
                try
                {
                    var n = new AssemblyName(e.Name).Name;
                    if (string.IsNullOrWhiteSpace(n) || !n.StartsWith("Siemens.Engineering", StringComparison.OrdinalIgnoreCase)) return null;
                    var f = Path.Combine(apiPath, n + ".dll");
                    return File.Exists(f) ? Assembly.LoadFrom(f) : null;
                }
                catch { return null; }
            };
            resolverInstalled = true;
        }

        Assembly LoadAssembly(string file, Action<string> log)
        {
            var path = Path.Combine(apiPath, file);
            if (!File.Exists(path)) return null;
            var existing = assemblies.FirstOrDefault(a => string.Equals(a.Location, path, StringComparison.OrdinalIgnoreCase));
            if (existing != null) return existing;
            var asm = Assembly.LoadFrom(path);
            assemblies.Add(asm);
            return asm;
        }

        IEnumerable<Type> Types(Assembly a, Action<string> log)
        {
            if (a == null) yield break;
            Type[] list;
            try { list = a.GetTypes(); }
            catch (ReflectionTypeLoadException ex)
            {
                list = ex.Types.Where(x => x != null).ToArray();
                foreach (var le in ex.LoaderExceptions.Where(x => x != null)) log("LoaderException: " + le.Message);
            }
            foreach (var t in list) yield return t;
        }

        Type FindType(string shortName, Action<string> log)
        {
            foreach (var f in Directory.GetFiles(apiPath, "Siemens.Engineering*.dll"))
            {
                Assembly a = null;
                try { a = LoadAssembly(Path.GetFileName(f), log); } catch { }
                if (a == null) continue;
                var direct = a.GetType("Siemens.Engineering." + shortName, false)
                    ?? a.GetType("Siemens.Engineering.HW.Features." + shortName, false)
                    ?? a.GetType("Siemens.Engineering.SW." + shortName, false)
                    ?? a.GetType("Siemens.Engineering.Upload." + shortName, false)
                    ?? a.GetType("Siemens.Engineering.Connection." + shortName, false);
                if (direct != null) return direct;
                var found = Types(a, log).FirstOrDefault(t => t.Name == shortName);
                if (found != null) return found;
            }
            return null;
        }

        void EnsurePortal(Action<string> log)
        {
            if (DetectInternal() == null) throw new DirectoryNotFoundException("TIA Portal V21 Openness non trovato.");
            if (!IsMemberOfOpennessGroup()) throw new UnauthorizedAccessException("Utente non nel gruppo 'Siemens TIA Openness'.");
            if (portal != null) return;

            EnsureResolver();
            var baseAsm = LoadAssembly("Siemens.Engineering.Base.dll", log);
            LoadAssembly("Siemens.Engineering.Step7.dll", log);
            var pt = baseAsm == null ? null : baseAsm.GetType("Siemens.Engineering.TiaPortal", false);
            var mt = baseAsm == null ? null : baseAsm.GetType("Siemens.Engineering.TiaPortalMode", false);
            if (pt == null || mt == null) throw new TypeLoadException("TiaPortal/TiaPortalMode non trovati nelle DLL V21.");

            log("Avvio TIA Portal V21 tramite Openness...");
            try { portal = Activator.CreateInstance(pt, new object[] { Enum.Parse(mt, "WithUserInterface") }); }
            catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
            log("TIA Portal V21 collegato.");
        }

        public List<PlcVariable> ImportProject(string file, Action<string> log)
        {
            EnsurePortal(log);
            object project = null;
            try
            {
                log("Apro progetto in sola lettura (Secondary): " + file);
                var projects = Prop(portal, "Projects");
                var modeType = FindType("ProjectOpenMode", log);
                if (modeType == null || !modeType.IsEnum) throw new TypeLoadException("ProjectOpenMode non trovato nelle DLL TIA V21.");
                var secondary = Enum.Parse(modeType, "Secondary");
                project = Call(projects, "Open", new FileInfo(Path.GetFullPath(file)), null, secondary);
                log("Progetto aperto in sola lettura.");
                return Scan(project, log);
            }
            finally { Close(project); }
        }

        public List<PlcVariable> UploadStation(string ip, string interfaceName, string plcPassword, Action<string> log)
        {
            EnsurePortal(log);
            object project = null;
            var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "PLCDataAnalyzer", "Uploads");
            Directory.CreateDirectory(root);
            try
            {
                project = Call(Prop(portal, "Projects"), "Create", new DirectoryInfo(root), "Upload_" + DateTime.Now.ToString("yyyyMMdd_HHmmss"));
                var providerType = FindType("StationUploadProvider", log);
                if (providerType == null) throw new TypeLoadException("StationUploadProvider non trovato.");
                var provider = GetService(project, providerType, log);
                if (provider == null) throw new InvalidOperationException("StationUploadProvider non disponibile. Vedi log servizi disponibili.");
                log("StationUploadProvider OK: " + provider.GetType().FullName);

                var mode = Call(Prop(Prop(provider, "Configuration"), "Modes"), "Find", "PN/IE");
                if (mode == null) throw new InvalidOperationException("Modalità PN/IE non disponibile.");
                var pc = SelectPcInterface(Prop(mode, "PcInterfaces"), interfaceName, ip, log);
                if (pc == null) throw new InvalidOperationException("Nessuna interfaccia PN/IE disponibile.");
                log("Interfaccia TIA: " + Convert.ToString(Prop(pc, "Name")));
                var address = Call(Prop(pc, "Addresses"), "Create", ip);

                var m = provider.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public)
                    .Where(x => x.Name == "StationUpload").OrderBy(x => x.GetParameters().Length)
                    .FirstOrDefault(x => x.GetParameters().Length == 2 || x.GetParameters().Length == 3);
                if (m == null) throw new MissingMethodException("StationUpload");
                var ps = m.GetParameters();
                var args = new object[ps.Length];
                args[0] = address;
                args[1] = MakeUploadCallback(ps[1].ParameterType, log, plcPassword);
                if (ps.Length == 3) args[2] = ps[2].HasDefaultValue ? ps[2].DefaultValue : (ps[2].ParameterType.IsEnum ? Enum.ToObject(ps[2].ParameterType, 0) : null);

                object result;
                try { result = m.Invoke(provider, args); }
                catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
                log("Upload state: " + Convert.ToString(Prop(result, "State")));
                if (Prop(result, "UploadedStation") == null) throw new InvalidOperationException("Upload non completato: UploadedStation nullo.");
                TryCall(project, "Save");
                return Scan(project, log);
            }
            finally { Close(project); }
        }

        object SelectPcInterface(object pcs, string requested, string ip, Action<string> log)
        {
            var list = Items(pcs).ToList();
            foreach (var p in list) log("TIA PN/IE: " + Convert.ToString(Prop(p, "Name")));
            foreach (var p in list)
            {
                try
                {
                    foreach (var d in Items(Call(p, "GetAccessibleDevices")))
                    {
                        var a = Convert.ToString(Prop(d, "Address"));
                        log("Accessible device: " + a + "  " + Convert.ToString(Prop(d, "Name")));
                        if (string.Equals(a, ip, StringComparison.OrdinalIgnoreCase)) return p;
                    }
                }
                catch (Exception ex) { log("Scan PN/IE: " + ex.Message); }
            }
            if (!string.IsNullOrWhiteSpace(requested))
            {
                var exact = list.FirstOrDefault(p => string.Equals(Convert.ToString(Prop(p, "Name")), requested, StringComparison.OrdinalIgnoreCase));
                if (exact != null) return exact;
            }
            return list.FirstOrDefault();
        }

        Delegate MakeUploadCallback(Type delegateType, Action<string> log, string password)
        {
            var invoke = delegateType.GetMethod("Invoke");
            var pars = invoke.GetParameters().Select(p => Expression.Parameter(p.ParameterType, p.Name)).ToArray();
            var handler = typeof(TiaService).GetMethod("HandleUploadConfiguration", BindingFlags.Instance | BindingFlags.NonPublic);
            var body = Expression.Call(Expression.Constant(this), handler, Expression.Convert(pars[0], typeof(object)), Expression.Constant(log), Expression.Constant(password, typeof(string)));
            return Expression.Lambda(delegateType, body, pars).Compile();
        }

        void HandleUploadConfiguration(object c, Action<string> log, string password)
        {
            if (c == null) return;
            var t = c.GetType();
            log("Upload callback: " + t.Name + " - " + Convert.ToString(Prop(c, "Message")));
            var setPassword = t.GetMethods().FirstOrDefault(m => m.Name == "SetPassword" && m.GetParameters().Length == 1);
            if (setPassword != null)
            {
                if (string.IsNullOrEmpty(password)) throw new InvalidOperationException("PLC richiede password. Inseriscila nel campo Password PLC.");
                var s = new SecureString(); foreach (var ch in password) s.AppendChar(ch); s.MakeReadOnly();
                setPassword.Invoke(c, new object[] { s });
                return;
            }
            var sel = t.GetProperty("CurrentSelection");
            if (sel != null && sel.CanWrite && sel.PropertyType.IsEnum)
            {
                var n = Enum.GetNames(sel.PropertyType).FirstOrDefault(x => x.IndexOf("Try", StringComparison.OrdinalIgnoreCase) >= 0)
                    ?? Enum.GetNames(sel.PropertyType).FirstOrDefault(x => !x.Equals("NoAction", StringComparison.OrdinalIgnoreCase));
                if (n != null) sel.SetValue(c, Enum.Parse(sel.PropertyType, n), null);
            }
        }

        List<PlcVariable> Scan(object project, Action<string> log)
        {
            var output = new List<PlcVariable>();
            var softwareContainerType = FindType("SoftwareContainer", log);
            if (softwareContainerType == null) throw new TypeLoadException("SoftwareContainer non trovato.");

            var devices = EnumerateProjectDevices(project, log).ToList();
            log("Dispositivi TIA trovati: " + devices.Count);
            var seenSoftware = new HashSet<object>();

            foreach (var device in devices)
            {
                var dn = Convert.ToString(Prop(device, "Name"));
                log("Scansione device: " + dn);
                foreach (var item in EnumerateDeviceItems(device))
                {
                    var itemName = Convert.ToString(Prop(item, "Name"));
                    object container = null;
                    try { container = GetService(item, softwareContainerType, log); }
                    catch (Exception ex) { log("Get SoftwareContainer " + itemName + ": " + ex.Message); }
                    if (container == null) continue;
                    var sw = Prop(container, "Software");
                    if (sw == null || !seenSoftware.Add(sw)) continue;
                    log("Software trovato: " + sw.GetType().FullName + "  Name=" + Convert.ToString(Prop(sw, "Name")));

                    if (Prop(sw, "TagTableGroup") != null || Prop(sw, "BlockGroup") != null)
                    {
                        ScanTags(sw, dn, output);
                        ScanBlocks(sw, dn, output, log);
                        ScanTypes(sw, dn, output);
                    }
                }
            }

            log("Variabili/oggetti estratti: " + output.Count);
            return output.GroupBy(x => (x.Source ?? "") + "|" + (x.Name ?? "") + "|" + (x.Group ?? "")).Select(g => g.First()).ToList();
        }

        IEnumerable<object> EnumerateProjectDevices(object project, Action<string> log)
        {
            foreach (var d in Items(Prop(project, "Devices"))) yield return d;
            foreach (var g in Items(Prop(project, "DeviceGroups")))
                foreach (var d in EnumerateDeviceGroup(g)) yield return d;
        }

        IEnumerable<object> EnumerateDeviceGroup(object group)
        {
            foreach (var d in Items(Prop(group, "Devices"))) yield return d;
            var groups = Prop(group, "Groups") ?? Prop(group, "DeviceGroups");
            foreach (var g in Items(groups)) foreach (var d in EnumerateDeviceGroup(g)) yield return d;
        }

        IEnumerable<object> EnumerateDeviceItems(object device)
        {
            var q = new Queue<object>();
            foreach (var x in Items(Prop(device, "DeviceItems") ?? Prop(device, "Items"))) q.Enqueue(x);
            while (q.Count > 0)
            {
                var n = q.Dequeue();
                yield return n;
                foreach (var c in Items(Prop(n, "Items") ?? Prop(n, "DeviceItems"))) q.Enqueue(c);
            }
        }

        void ScanTags(object sw, string device, List<PlcVariable> output)
        {
            WalkTagGroup(Prop(sw, "TagTableGroup"), device, "", output);
        }

        void WalkTagGroup(object group, string device, string path, List<PlcVariable> output)
        {
            if (group == null) return;
            foreach (var table in Items(Prop(group, "TagTables")))
            {
                var tn = Convert.ToString(Prop(table, "Name"));
                foreach (var tag in Items(Prop(table, "Tags")))
                    output.Add(new PlcVariable { Source = "PLC Tag", Name = Convert.ToString(Prop(tag, "Name")), DataType = Convert.ToString(Prop(tag, "DataTypeName")), Address = Convert.ToString(Prop(tag, "LogicalAddress")), Group = device + " / " + path + tn });
            }
            foreach (var child in Items(Prop(group, "Groups")))
            {
                var n = Convert.ToString(Prop(child, "Name"));
                WalkTagGroup(child, device, path + n + "/", output);
            }
        }

        void ScanBlocks(object sw, string device, List<PlcVariable> output, Action<string> log)
        {
            WalkBlockGroup(Prop(sw, "BlockGroup"), device, "", output, log);
        }

        void WalkBlockGroup(object group, string device, string path, List<PlcVariable> output, Action<string> log)
        {
            if (group == null) return;
            foreach (var b in Items(Prop(group, "Blocks")))
            {
                var type = b.GetType().Name;
                if (type.IndexOf("DataBlock", StringComparison.OrdinalIgnoreCase) < 0) continue;
                var name = Convert.ToString(Prop(b, "Name"));
                output.Add(new PlcVariable { Source = "DB", Name = name, DataType = type, Address = "symbolic", Group = device + " / " + path });
                TryExtractBlockMembers(b, name, device + " / " + path, output, log);
            }
            foreach (var child in Items(Prop(group, "Groups")))
            {
                var n = Convert.ToString(Prop(child, "Name"));
                WalkBlockGroup(child, device, path + n + "/", output, log);
            }
        }

        void TryExtractBlockMembers(object block, string dbName, string group, List<PlcVariable> output, Action<string> log)
        {
            string temp = null;
            try
            {
                var export = block.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public)
                    .FirstOrDefault(m => m.Name == "Export" && m.GetParameters().Length == 2 && m.GetParameters()[0].ParameterType == typeof(FileInfo));
                if (export == null) return;
                var optType = export.GetParameters()[1].ParameterType;
                object opt;
                try { opt = Enum.Parse(optType, "WithDefaults"); } catch { opt = Enum.ToObject(optType, 0); }
                temp = Path.Combine(Path.GetTempPath(), "PDA_" + Guid.NewGuid().ToString("N") + ".xml");
                export.Invoke(block, new object[] { new FileInfo(temp), opt });
                if (!File.Exists(temp)) return;
                var doc = XDocument.Load(temp);
                foreach (var m in doc.Descendants().Where(x => x.Name.LocalName == "Member" && x.Attribute("Name") != null))
                {
                    var chain = m.AncestorsAndSelf().Where(x => x.Name.LocalName == "Member" && x.Attribute("Name") != null).Reverse().Select(x => (string)x.Attribute("Name")).ToArray();
                    if (chain.Length == 0) continue;
                    var p = string.Join(".", chain);
                    var dt = (string)m.Attribute("Datatype") ?? (string)m.Attribute("DataType") ?? "";
                    output.Add(new PlcVariable { Source = "DB Var", Name = dbName + "." + p, DataType = dt, Address = dbName + "." + p, Group = group + dbName });
                }
            }
            catch (Exception ex) { log("DB " + dbName + " export: " + (ex is TargetInvocationException && ex.InnerException != null ? ex.InnerException.Message : ex.Message)); }
            finally { try { if (temp != null && File.Exists(temp)) File.Delete(temp); } catch { } }
        }

        void ScanTypes(object sw, string device, List<PlcVariable> output)
        {
            WalkTypeGroup(Prop(sw, "TypeGroup"), device, "", output);
        }

        void WalkTypeGroup(object group, string device, string path, List<PlcVariable> output)
        {
            if (group == null) return;
            foreach (var t in Items(Prop(group, "Types")))
                output.Add(new PlcVariable { Source = "UDT", Name = Convert.ToString(Prop(t, "Name")), DataType = t.GetType().Name, Address = "symbolic", Group = device + " / " + path });
            foreach (var child in Items(Prop(group, "Groups")))
            {
                var n = Convert.ToString(Prop(child, "Name"));
                WalkTypeGroup(child, device, path + n + "/", output);
            }
        }

        object GetService(object target, Type serviceType, Action<string> log)
        {
            if (target == null || serviceType == null) return null;
            var providerType = FindType("IEngineeringServiceProvider", log);
            if (providerType == null) throw new TypeLoadException("IEngineeringServiceProvider non trovato.");
            if (!providerType.IsAssignableFrom(target.GetType())) return null;

            var getService = providerType.GetMethods().FirstOrDefault(m => m.Name == "GetService" && m.IsGenericMethodDefinition && m.GetParameters().Length == 0);
            if (getService == null) throw new MissingMethodException(providerType.FullName, "GetService<T>");
            try
            {
                return getService.MakeGenericMethod(serviceType).Invoke(target, null);
            }
            catch (TargetInvocationException ex)
            {
                throw ex.InnerException ?? ex;
            }
        }

        static object Prop(object obj, string name)
        {
            if (obj == null) return null;
            try { return obj.GetType().GetProperty(name, BindingFlags.Instance | BindingFlags.Public)?.GetValue(obj, null); } catch { return null; }
        }

        static object Call(object obj, string name, params object[] args)
        {
            if (obj == null) return null;
            Exception last = null;
            foreach (var m in obj.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public).Where(x => x.Name == name && x.GetParameters().Length == args.Length))
            {
                try { return m.Invoke(obj, args); }
                catch (TargetInvocationException ex) { throw ex.InnerException ?? ex; }
                catch (Exception ex) { last = ex; }
            }
            if (last != null) throw last;
            throw new MissingMethodException(obj.GetType().FullName, name);
        }

        static void TryCall(object obj, string name)
        {
            if (obj == null) return;
            try { obj.GetType().GetMethod(name, Type.EmptyTypes)?.Invoke(obj, null); } catch { }
        }

        static void Close(object obj) { TryCall(obj, "Close"); }

        static IEnumerable<object> Items(object collection)
        {
            var e = collection as IEnumerable;
            if (e == null) yield break;
            foreach (var x in e) if (x != null) yield return x;
        }
    }
}
