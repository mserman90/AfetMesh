import os
import sys
import json
import time
import socket
import threading
import uuid
import base64
import webbrowser
import numpy as np
import winsound
from io import BytesIO
from PIL import Image, ImageTk
import tkinter as tk
from tkinter import ttk, messagebox, simpledialog

# Audio & Video imports
try:
    import sounddevice as sd
    HAS_AUDIO = True
except Exception as e:
    HAS_AUDIO = False

try:
    import cv2
    HAS_OPENCV = True
except Exception as e:
    HAS_OPENCV = False

# Protocol Constants
UDP_PORT = 8888
TCP_PORT = 8889

class MeshPacket:
    def __init__(self, packet_type, payload="", sender_id="", sender_name="Windows PC", recipient_id="*", sos_status=None, battery=100, lat=None, lon=None, ttl=5, hops=0, packet_id=None):
        self.id = packet_id or str(uuid.uuid4())
        self.senderId = sender_id
        self.senderName = sender_name
        self.recipientId = recipient_id
        self.type = packet_type  # DISCOVERY_BEACON, CHAT_TEXT, EMERGENCY_SOS, AUDIO_STREAM, VIDEO_FRAME, VOICE_NOTE
        self.payload = payload
        self.timestamp = int(time.time() * 1000)
        self.ttl = ttl
        self.hops = hops
        self.senderBattery = battery
        self.latitude = lat
        self.longitude = lon
        self.sosStatus = sos_status

    def to_dict(self):
        return {
            "id": self.id,
            "senderId": self.senderId,
            "senderName": self.senderName,
            "recipientId": self.recipientId,
            "type": self.type,
            "payload": self.payload,
            "timestamp": self.timestamp,
            "ttl": self.ttl,
            "hops": self.hops,
            "senderBattery": self.senderBattery,
            "latitude": self.latitude,
            "longitude": self.longitude,
            "sosStatus": self.sosStatus
        }

    @staticmethod
    def from_dict(d):
        sender_id = d.get("senderId") or d.get("sender_id") or d.get("userId") or d.get("user_id") or d.get("device_id") or "EXT_PC_" + str(uuid.uuid4())[:6]
        sender_name = d.get("senderName") or d.get("sender_name") or d.get("userName") or d.get("name") or "Diğer Afet Cihazı"
        payload = d.get("payload") or d.get("message") or d.get("msg") or d.get("text") or ""
        sos_status = d.get("sosStatus") or d.get("sos_status") or d.get("status")
        packet_type = d.get("type") or d.get("packet_type") or ("EMERGENCY_SOS" if sos_status else "CHAT_TEXT")
        lat = d.get("latitude") if d.get("latitude") is not None else d.get("lat")
        lon = d.get("longitude") if d.get("longitude") is not None else (d.get("lng") or d.get("lon"))

        return MeshPacket(
            packet_type=packet_type,
            payload=payload,
            sender_id=sender_id,
            sender_name=sender_name,
            recipient_id=d.get("recipientId") or d.get("recipient_id") or "*",
            sos_status=sos_status,
            battery=d.get("senderBattery") or d.get("battery", -1),
            lat=lat,
            lon=lon,
            ttl=d.get("ttl", 5),
            hops=d.get("hops", 0),
            packet_id=d.get("id") or d.get("packet_id") or d.get("msg_id")
        )

class DesktopMeshEngine:
    def __init__(self, on_packet_received=None, on_peer_updated=None, on_video_frame=None, on_sos_alert=None):
        self.node_id = "NODE_PC_" + str(uuid.uuid4())[:6]
        self.device_name = f"Bilgisayar ({socket.gethostname()})"
        self.peers = {}  # peer_id -> dict
        self.received_packet_ids = set()
        self.active_sos_status = None
        self.is_running = False

        self.on_packet_received = on_packet_received
        self.on_peer_updated = on_peer_updated
        self.on_video_frame = on_video_frame
        self.on_sos_alert = on_sos_alert

        self.udp_socket = None
        self.tcp_socket = None

    def start(self):
        self.is_running = True
        threading.Thread(target=self._udp_listener, daemon=True).start()
        threading.Thread(target=self._udp_broadcaster, daemon=True).start()
        threading.Thread(target=self._tcp_server, daemon=True).start()
        threading.Thread(target=self._peer_cleanup_loop, daemon=True).start()

    def _udp_listener(self):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('', UDP_PORT))
            self.udp_socket = sock

            while self.is_running:
                data, addr = sock.recvfrom(65535)
                sender_ip = addr[0]
                if self._is_local_ip(sender_ip):
                    continue
                try:
                    raw_str = data.decode('utf-8')
                    d = json.loads(raw_str)
                    packet = MeshPacket.from_dict(d)
                    if packet.type == "DISCOVERY_BEACON":
                        self._update_peer(packet.senderId, packet.senderName, sender_ip, packet.senderBattery, packet.sosStatus)
                except Exception as e:
                    pass
        except Exception as e:
            print("UDP Listener error:", e)

    def _udp_broadcaster(self):
        while self.is_running:
            try:
                beacon = MeshPacket(
                    packet_type="DISCOVERY_BEACON",
                    sender_id=self.node_id,
                    sender_name=self.device_name,
                    battery=100,
                    sos_status=self.active_sos_status
                )
                data = json.dumps(beacon.to_dict()).encode('utf-8')
                sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                sock.sendto(data, ('<broadcast>', UDP_PORT))
                sock.close()
            except Exception as e:
                pass
            time.sleep(3)

    def _tcp_server(self):
        try:
            server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            server.bind(('', TCP_PORT))
            server.listen(10)
            self.tcp_socket = server

            while self.is_running:
                client, addr = server.accept()
                threading.Thread(target=self._handle_tcp_client, args=(client, addr[0]), daemon=True).start()
        except Exception as e:
            print("TCP Server error:", e)

    def _handle_tcp_client(self, client_socket, sender_ip):
        try:
            client_socket.settimeout(10.0)
            buffer = ""
            while True:
                chunk = client_socket.recv(65536).decode('utf-8')
                if not chunk:
                    break
                buffer += chunk
                if "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    d = json.loads(line)
                    packet = MeshPacket.from_dict(d)
                    self._process_packet(packet, sender_ip)
            client_socket.close()
        except Exception as e:
            pass

    def send_packet(self, packet):
        self.received_packet_ids.add(packet.id)
        raw_json = json.dumps(packet.to_dict()) + "\n"

        # Direct TCP unicast or broadcast
        for peer_id, peer in list(self.peers.items()):
            if packet.recipientId == "*" or packet.recipientId == peer_id:
                threading.Thread(target=self._send_tcp_raw, args=(peer['ip'], peer['port'], raw_json), daemon=True).start()

        # Multi-hop flooding to intermediate nodes if target is not direct
        if packet.recipientId != "*" and packet.recipientId not in self.peers:
            for peer_id, peer in list(self.peers.items()):
                threading.Thread(target=self._send_tcp_raw, args=(peer['ip'], peer['port'], raw_json), daemon=True).start()

        # Subnet UDP Broadcast for off-grid discovery
        threading.Thread(target=self._send_udp_broadcast, args=(raw_json,), daemon=True).start()

    def _send_udp_broadcast(self, raw_json):
        try:
            data = raw_json.strip().encode('utf-8')
            for port in [UDP_PORT, 8888, 8000, 9999]:
                try:
                    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                    sock.sendto(data, ('<broadcast>', port))
                    sock.close()
                except Exception:
                    pass
        except Exception:
            pass

    def _send_tcp_raw(self, ip, port, raw_json):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(3.0)
            s.connect((ip, port))
            s.sendall(raw_json.encode('utf-8'))
            s.close()
        except Exception as e:
            pass

    def _process_packet(self, packet, sender_ip):
        if packet.id in self.received_packet_ids:
            return
        self.received_packet_ids.add(packet.id)

        if packet.senderId != self.node_id:
            self._update_peer(packet.senderId, packet.senderName, sender_ip, packet.senderBattery, packet.sosStatus)

        if packet.type == "VIDEO_FRAME" and self.on_video_frame:
            self.on_video_frame(packet.senderId, packet.payload)
            return

        if packet.type == "EMERGENCY_SOS" and self.on_sos_alert:
            self.on_sos_alert(packet)

        if self.on_packet_received:
            self.on_packet_received(packet)

        # Briar & Meshenger Multi-Hop Store & Forward Relay
        if packet.ttl > 1:
            relayed_dict = packet.to_dict()
            relayed_dict['ttl'] -= 1
            relayed_dict['hops'] += 1
            raw_json = json.dumps(relayed_dict) + "\n"

            # 1. TCP relay to direct peers except original sender
            for p_id, peer in list(self.peers.items()):
                if peer['ip'] != sender_ip:
                    threading.Thread(target=self._send_tcp_raw, args=(peer['ip'], peer['port'], raw_json), daemon=True).start()

            # 2. Subnet UDP Broadcast relay
            threading.Thread(target=self._send_udp_broadcast, args=(raw_json,), daemon=True).start()

    def _update_peer(self, peer_id, name, ip, battery, sos_status):
        self.peers[peer_id] = {
            "id": peer_id,
            "name": name,
            "ip": ip,
            "port": TCP_PORT,
            "battery": battery,
            "sosStatus": sos_status,
            "lastSeen": time.time()
        }
        if self.on_peer_updated:
            self.on_peer_updated(self.peers)

    def _peer_cleanup_loop(self):
        while self.is_running:
            time.sleep(10)
            now = time.time()
            changed = False
            for p_id, peer in list(self.peers.items()):
                if now - peer['lastSeen'] > 25:
                    del self.peers[p_id]
                    changed = True
            if changed and self.on_peer_updated:
                self.on_peer_updated(self.peers)

    def _is_local_ip(self, ip):
        try:
            return ip in ["127.0.0.1", socket.gethostbyname(socket.gethostname())]
        except:
            return False

# Desktop GUI App
class AfetMeshDesktopApp:
    def __init__(self, root):
        self.root = root
        self.root.title("AfetMesh - Off-Grid Masaüstü Afet Haberleşmesi")
        self.root.geometry("900dialog" if False else "950x680")
        self.root.configure(bg="#1e1e2e")

        self.siren_playing = False
        self.whistle_playing = False
        self.is_recording_ptt = False
        self.is_webcam_streaming = False

        self.messages = []
        self.sos_alerts = []

        self.profile_path = os.path.join(os.path.expanduser("~"), ".afetmesh_pc_profile.json")
        saved_name, self.is_kvkk_accepted = self._load_saved_profile()

        self.engine = DesktopMeshEngine(
            on_packet_received=self._on_packet_received,
            on_peer_updated=self._on_peer_updated,
            on_video_frame=self._on_video_frame,
            on_sos_alert=self._on_sos_alert
        )
        if saved_name:
            self.engine.device_name = saved_name

        self._setup_ui()
        self.engine.start()

        if not self.is_kvkk_accepted:
            self.root.after(300, lambda: self._show_kvkk_dialog(mandatory=True))

    def _load_saved_profile(self):
        try:
            if os.path.exists(self.profile_path):
                with open(self.profile_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    return data.get("user_name"), data.get("is_kvkk_accepted", False)
        except:
            pass
        return None, False

    def _save_profile(self, name=None, kvkk_accepted=None):
        try:
            data = {}
            if os.path.exists(self.profile_path):
                with open(self.profile_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
            if name is not None:
                data["user_name"] = name
            if kvkk_accepted is not None:
                data["is_kvkk_accepted"] = kvkk_accepted
                data["kvkk_timestamp"] = int(time.time())
            with open(self.profile_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False)
        except Exception as e:
            print("Failed to save profile:", e)

    def _show_kvkk_dialog(self, mandatory=False):
        top = tk.Toplevel(self.root)
        top.title("KVKK Aydınlatma & Açık Rıza Beyanı")
        top.geometry("620x480")
        top.configure(bg="#1e1e2e")
        top.transient(self.root)
        top.grab_set()

        lbl_title = tk.Label(top, text="🔒 6698 Sayılı KVKK Aydınlatma Metni", font=("Segoe UI", 12, "bold"), fg="#89b4fa", bg="#1e1e2e")
        lbl_title.pack(pady=10)

        txt_frame = tk.Frame(top, bg="#181825")
        txt_frame.pack(fill=tk.BOTH, expand=True, padx=15, pady=5)

        txt = tk.Text(txt_frame, bg="#181825", fg="#cdd6f4", font=("Segoe UI", 9), wrap=tk.WORD)
        txt.pack(fill=tk.BOTH, expand=True, side=tk.LEFT, padx=5, pady=5)
        
        scroll = ttk.Scrollbar(txt_frame, command=txt.yview)
        scroll.pack(side=tk.RIGHT, fill=tk.Y)
        txt.config(yscrollcommand=scroll.set)

        notice_text = (
            "AfetMesh Masaüstü Uygulaması KVKK Aydınlatma Metni:\n\n"
            "1. İŞLENEN VERİLER: Kullanıcı Rumuzu/Adı, Cihaz Kimlik Kodu (UUID), Acil Durum SOS Bildirimleri ve Mesajlar.\n\n"
            "2. İŞLEME AMACI: Doğal afet anlarında GSM veya internet altyapısı kesildiğinde yerel P2P/Mesh ağı üzerinden yardım çağrılarının iletilmesi.\n\n"
            "3. VERİ GÜVENLİĞİ: Verileriniz herhangi bir merkezi sunucuda saklanmaz; yalnızca kapsama alanındaki doğrudan bağlı cihazlara yerel sinyal olarak iletilir.\n\n"
            "4. HAKLARINIZ: KVKK Madde 11 uyarınca dilediğiniz zaman rızanızı silebilir veya profilinizi düzenleyebilirsiniz."
        )
        txt.insert(tk.END, notice_text)
        txt.config(state=tk.DISABLED)

        var_check = tk.BooleanVar(value=self.is_kvkk_accepted)

        def on_accept():
            if mandatory and not var_check.get():
                messagebox.showwarning("KVKK Onayı", "Uygulamayı kullanabilmek için lütfen KVKK Aydınlatma Metnini onaylayın.")
                return
            self.is_kvkk_accepted = True
            self._save_profile(kvkk_accepted=True)
            top.destroy()

        chk = tk.Checkbutton(
            top,
            text="KVKK Aydınlatma Metnini okudum. Afet anında bildirimlerimin mesh ağıyla paylaşılmasını onaylıyorum.",
            variable=var_check,
            font=("Segoe UI", 9),
            fg="#a6e3a1",
            bg="#1e1e2e",
            selectcolor="#1e1e2e"
        )
        chk.pack(pady=10)

        btn_confirm = tk.Button(top, text="OKUDUM VE KABUL EDİYORUM", font=("Segoe UI", 10, "bold"), bg="#a6e3a1", fg="#11111b", command=on_accept)
        btn_confirm.pack(pady=(0, 15))

    def _setup_ui(self):
        # Top Header Bar
        header = tk.Frame(self.root, bg="#11111b", height=50)
        header.pack(fill=tk.X, side=tk.TOP)

        title_lbl = tk.Label(header, text="🚨 AfetMesh PC", font=("Segoe UI", 14, "bold"), fg="#f38ba8", bg="#11111b")
        title_lbl.pack(side=tk.LEFT, padx=12, pady=8)

        self.user_lbl = tk.Label(header, text=f"👤 Kullanıcı: {self.engine.device_name}", font=("Segoe UI", 10, "bold"), fg="#89b4fa", bg="#11111b")
        self.user_lbl.pack(side=tk.LEFT, padx=10)

        edit_btn = tk.Button(header, text="✏️ İsim Düzenle", font=("Segoe UI", 9, "bold"), bg="#313244", fg="#cdd6f4", command=self._edit_user_name)
        edit_btn.pack(side=tk.LEFT, padx=5)

        kvkk_btn = tk.Button(header, text="🔒 KVKK Metni", font=("Segoe UI", 9, "bold"), bg="#313244", fg="#89b4fa", command=lambda: self._show_kvkk_dialog(mandatory=False))
        kvkk_btn.pack(side=tk.LEFT, padx=5)

        self.status_lbl = tk.Label(header, text=f"ID: {self.engine.node_id} | Bağlı Cihaz: 0", font=("Segoe UI", 10), fg="#a6adc8", bg="#11111b")
        self.status_lbl.pack(side=tk.RIGHT, padx=15)

    def _edit_user_name(self):
        new_name = simpledialog.askstring("Kullanıcı Profili", "Afet mesh ağında görülecek Adınız & Soyadınız:", initialvalue=self.engine.device_name)
        if new_name and new_name.strip():
            clean_name = new_name.strip()
            self.engine.device_name = clean_name
            self._save_user_name(clean_name)
            self.user_lbl.config(text=f"👤 Kullanıcı: {clean_name}")
            messagebox.showinfo("Profil Güncellendi", f"Kullanıcı adınız başarıyla kaydedildi:\n{clean_name}")

        # Tab Notebook
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("TNotebook", background="#1e1e2e", borderwidth=0)
        style.configure("TNotebook.Tab", background="#313244", foreground="#cdd6f4", padding=[12, 6], font=("Segoe UI", 10, "bold"))
        style.map("TNotebook.Tab", background=[("selected", "#89b4fa")], foreground=[("selected", "#11111b")])

        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)

        # Tabs
        self.tab_sos = tk.Frame(self.notebook, bg="#1e1e2e")
        self.tab_chat = tk.Frame(self.notebook, bg="#1e1e2e")
        self.tab_ptt = tk.Frame(self.notebook, bg="#1e1e2e")
        self.tab_video = tk.Frame(self.notebook, bg="#1e1e2e")
        self.tab_radar = tk.Frame(self.notebook, bg="#1e1e2e")

        self.notebook.add(self.tab_sos, text="🚨 ACİL SOS")
        self.notebook.add(self.tab_chat, text="💬 Yazılı Chat")
        self.notebook.add(self.tab_ptt, text="🎙️ Sesli (PTT Telsiz)")
        self.notebook.add(self.tab_video, text="📹 Görüntülü")
        self.notebook.add(self.tab_radar, text="📡 Mesh Radar")

        self._build_sos_tab()
        self._build_chat_tab()
        self._build_ptt_tab()
        self._build_video_tab()
        self._build_radar_tab()

    # --- 1. SOS TAB ---
    def _build_sos_tab(self):
        f = self.tab_sos
        lbl = tk.Label(f, text="ACİL AFET SOS YAYINI", font=("Segoe UI", 14, "bold"), fg="#f38ba8", bg="#1e1e2e")
        lbl.pack(pady=10)

        btn_grid = tk.Frame(f, bg="#1e1e2e")
        btn_grid.pack(pady=10)

        statuses = [
            ("🚨 ENKAZ ALTINDAYIM", "#f38ba8"),
            ("🤕 YARALIYIM", "#fab387"),
            ("🚑 ACİL YARDIM LAZIM", "#f9e2af"),
            ("👍 GÜVENDEYİM", "#a6e3a1"),
            ("💧 SU / GIDA LAZIM", "#89b4fa")
        ]

        for text, color in statuses:
            btn = tk.Button(
                btn_grid, text=text, font=("Segoe UI", 11, "bold"),
                bg=color, fg="#11111b", width=24, height=2,
                command=lambda t=text: self._send_sos_broadcast(t)
            )
            btn.pack(pady=4)

        siren_frame = tk.Frame(f, bg="#1e1e2e")
        siren_frame.pack(pady=15)

        self.btn_siren = tk.Button(siren_frame, text="🔊 PC Alarm Sirenini Başlat", font=("Segoe UI", 10, "bold"), bg="#ef4444", fg="white", command=self._toggle_pc_siren)
        self.btn_siren.pack(side=tk.LEFT, padx=5)

        self.btn_whistle = tk.Button(siren_frame, text="🔊 PC Dijital Düdük (3.2 kHz)", font=("Segoe UI", 10, "bold"), bg="#fe640b", fg="white", command=self._toggle_pc_whistle)
        self.btn_whistle.pack(side=tk.LEFT, padx=5)

        # Active SOS Feed
        sos_list_lbl = tk.Label(f, text="Ağdaki Aktif Acil SOS Çağrıları:", font=("Segoe UI", 11, "bold"), fg="#f38ba8", bg="#1e1e2e")
        sos_list_lbl.pack(anchor="w", padx=20, pady=(10, 2))

        self.sos_txt = tk.Text(f, height=8, bg="#181825", fg="#f38ba8", font=("Consolas", 10), state=tk.DISABLED)
        self.sos_txt.pack(fill=tk.X, padx=20, pady=5)

    def _send_sos_broadcast(self, status_text):
        if "GÜVENDE" in status_text.upper():
            # Stop siren if active
            if self.siren_playing:
                self.siren_playing = False
                self.btn_siren.config(text="🔊 PC Alarm Sirenini Başlat", bg="#ef4444", fg="white")
            self.engine.active_sos_status = None
            packet = MeshPacket(
                packet_type="EMERGENCY_SOS",
                payload=f"PC DURUM BİLDİRİMİ: {status_text}",
                sender_id=self.engine.node_id,
                sender_name=self.engine.device_name,
                sos_status=status_text
            )
            self.engine.send_packet(packet)
            messagebox.showinfo("Durum Güncellendi", f"Güvende olduğunuz bildirildi. Aktif SOS alarmları durduruldu:\n{status_text}")
        else:
            self.engine.active_sos_status = status_text
            packet = MeshPacket(
                packet_type="EMERGENCY_SOS",
                payload=f"PC ACİL SOS: {status_text}",
                sender_id=self.engine.node_id,
                sender_name=self.engine.device_name,
                sos_status=status_text
            )
            self.engine.send_packet(packet)
            messagebox.showinfo("SOS Yayınlandı", f"SOS Uyarısı tüm Android telefonlara iletildi:\n{status_text}")

    def _toggle_pc_siren(self):
        self.siren_playing = not self.siren_playing
        if self.siren_playing:
            self.btn_siren.config(text="🛑 PC Alarm Sirenini Durdur", bg="#94e2d5", fg="#11111b")
            threading.Thread(target=self._siren_loop, daemon=True).start()
        else:
            self.btn_siren.config(text="🔊 PC Alarm Sirenini Başlat", bg="#ef4444", fg="white")

    def _siren_loop(self):
        while self.siren_playing:
            try:
                winsound.Beep(2500, 400)
                winsound.Beep(1500, 400)
            except:
                break

    def _toggle_pc_whistle(self):
        self.whistle_playing = not self.whistle_playing
        if self.whistle_playing:
            self.btn_whistle.config(text="🛑 PC Dijital Düdük Durdur", bg="#fab387", fg="#11111b")
            threading.Thread(target=self._whistle_loop, daemon=True).start()
        else:
            self.btn_whistle.config(text="🔊 PC Dijital Düdük (3.2 kHz)", bg="#fe640b", fg="white")

    def _whistle_loop(self):
        while self.whistle_playing:
            try:
                winsound.Beep(3200, 450)
                time.sleep(0.1)
            except:
                break

    # --- 2. CHAT TAB ---
    def _build_chat_tab(self):
        f = self.tab_chat
        self.chat_display = tk.Text(f, bg="#181825", fg="#cdd6f4", font=("Segoe UI", 10), state=tk.DISABLED)
        self.chat_display.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)

        # Input Row
        input_row = tk.Frame(f, bg="#1e1e2e")
        input_row.pack(fill=tk.X, padx=10, pady=(0, 10))

        self.chat_entry = tk.Entry(input_row, bg="#313244", fg="white", font=("Segoe UI", 11), insertbackground="white")
        self.chat_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 8))
        self.chat_entry.bind("<Return>", lambda e: self._send_chat())

        btn_send = tk.Button(input_row, text="Gönder", font=("Segoe UI", 10, "bold"), bg="#89b4fa", fg="#11111b", command=self._send_chat)
        btn_send.pack(side=tk.RIGHT)

    def _send_chat(self):
        text = self.chat_entry.get().strip()
        if not text:
            return
        self.chat_entry.delete(0, tk.END)

        packet = MeshPacket(
            packet_type="CHAT_TEXT",
            payload=text,
            sender_id=self.engine.node_id,
            sender_name=self.engine.device_name
        )
        self.engine.send_packet(packet)
        self._append_chat(f"[SİZ - PC] {text}")

    def _append_chat(self, msg):
        self.chat_display.config(state=tk.NORMAL)
        self.chat_display.insert(tk.END, msg + "\n")
        self.chat_display.see(tk.END)
        self.chat_display.config(state=tk.DISABLED)

    # --- 3. PTT VOICE TAB ---
    def _build_ptt_tab(self):
        f = self.tab_ptt
        lbl = tk.Label(f, text="PUSH-TO-TALK (TELSİZ MODU)", font=("Segoe UI", 14, "bold"), fg="#89b4fa", bg="#1e1e2e")
        lbl.pack(pady=15)

        self.ptt_btn = tk.Button(f, text="🎙️ MİKROFON İLE BAS-KONUŞ\n(Basılı Tutun)", font=("Segoe UI", 14, "bold"), bg="#89b4fa", fg="#11111b", height=5, width=30)
        self.ptt_btn.pack(pady=20)

        self.ptt_btn.bind("<ButtonPress-1>", self._start_ptt_recording)
        self.ptt_btn.bind("<ButtonRelease-1>", self._stop_ptt_recording)

        self.audio_status = tk.Label(f, text="Dinlemede: Gelen sesler bilgisayar hoparlöründen çalınacaktır.", font=("Segoe UI", 10), fg="#a6adc8", bg="#1e1e2e")
        self.audio_status.pack(pady=10)

    def _start_ptt_recording(self, event):
        if not HAS_AUDIO:
            messagebox.showerror("Hata", "sounddevice kütüphanesi bulunamadı.")
            return
        self.is_recording_ptt = True
        self.ptt_btn.config(bg="#f38ba8", text="🎙️ CANLI SES YAYINI YAPILIYOR...\n(Bırakınca Durur)")
        threading.Thread(target=self._ptt_stream_loop, daemon=True).start()

    def _stop_ptt_recording(self, event):
        self.is_recording_ptt = False
        self.ptt_btn.config(bg="#89b4fa", text="🎙️ MİKROFON İLE BAS-KONUŞ\n(Basılı Tutun)")

    def _ptt_stream_loop(self):
        try:
            samplerate = 16000
            blocksize = 1600
            with sd.InputStream(samplerate=samplerate, channels=1, dtype='int16') as stream:
                while self.is_recording_ptt:
                    data, overflow = stream.read(blocksize)
                    b64 = base64.b64encode(data.tobytes()).decode('utf-8')
                    packet = MeshPacket(
                        packet_type="AUDIO_STREAM",
                        payload=b64,
                        sender_id=self.engine.node_id,
                        sender_name=self.engine.device_name
                    )
                    self.engine.send_packet(packet)
        except Exception as e:
            print("PTT Stream Loop Error:", e)

    def _play_pcm_chunk(self, base64_chunk):
        if not HAS_AUDIO:
            return
        try:
            pcm_bytes = base64.b64decode(base64_chunk)
            audio_array = np.frombuffer(pcm_bytes, dtype=np.int16)
            sd.play(audio_array, samplerate=16000)
        except Exception as e:
            pass

    # --- 4. VIDEO TAB ---
    def _build_video_tab(self):
        f = self.tab_video
        lbl = tk.Label(f, text="OFF-GRID GÖRÜNTÜLÜ AKIŞ", font=("Segoe UI", 12, "bold"), fg="#89b4fa", bg="#1e1e2e")
        lbl.pack(pady=5)

        self.video_canvas = tk.Label(f, bg="#11111b", width=360, height=270, text="Gelen Canlı Görüntü Yok", fg="#6c7086")
        self.video_canvas.pack(pady=10)

        webcam_btn = tk.Button(f, text="📹 Bilgisayar Web kamerasını Yayınla", font=("Segoe UI", 10, "bold"), bg="#a6e3a1", fg="#11111b", command=self._toggle_webcam_stream)
        webcam_btn.pack(pady=5)

    def _toggle_webcam_stream(self):
        if not HAS_OPENCV:
            messagebox.showerror("Hata", "OpenCV kütüphanesi yüklenemedi.")
            return
        self.is_webcam_streaming = not self.is_webcam_streaming
        if self.is_webcam_streaming:
            threading.Thread(target=self._webcam_stream_loop, daemon=True).start()
            messagebox.showinfo("WebCam", "Web Kasa yayını başlatıldı.")

    def _webcam_stream_loop(self):
        cap = cv2.VideoCapture(0)
        while self.is_webcam_streaming and cap.isOpened():
            ret, frame = cap.read()
            if ret:
                frame_resized = cv2.resize(frame, (320, 240))
                _, buffer = cv2.imencode('.jpg', frame_resized, [int(cv2.IMWRITE_JPEG_QUALITY), 45])
                b64_frame = base64.b64encode(buffer).decode('utf-8')

                packet = MeshPacket(
                    packet_type="VIDEO_FRAME",
                    payload=b64_frame,
                    sender_id=self.engine.node_id,
                    sender_name=self.engine.device_name
                )
                self.engine.send_packet(packet)
            time.sleep(0.1) # 10 FPS
        cap.release()

    # --- 5. AFET HARİTASI & AFAD TAB ---
    def _build_map_tab(self):
        f = self.tab_map
        lbl = tk.Label(f, text="🗺️ ÇEVRİMDİŞİ AFET HARİTASI & e-DEVLET AFAD TOPLANMA ALANLARI", font=("Segoe UI", 12, "bold"), fg="#a6e3a1", bg="#1e1e2e")
        lbl.pack(pady=10)

        # e-Devlet AFAD Direct Link Banner
        edevlet_frame = tk.Frame(f, bg="#11111b", padx=10, pady=8)
        edevlet_frame.pack(fill=tk.X, padx=15, pady=5)

        banner_lbl = tk.Label(edevlet_frame, text="✅ Kaynak: https://www.turkiye.gov.tr/afet-ve-acil-durum-yonetimi-acil-toplanma-alani-sorgulama", font=("Segoe UI", 9, "bold"), fg="#a6e3a1", bg="#11111b")
        banner_lbl.pack(side=tk.LEFT, padx=5)

        btn_open_edevlet = tk.Button(
            edevlet_frame,
            text="🌐 e-Devlet'te Acil Toplanma Alanı Sorgula",
            font=("Segoe UI", 9, "bold"),
            bg="#d32f2f",
            fg="white",
            command=lambda: webbrowser.open("https://www.turkiye.gov.tr/afet-ve-acil-durum-yonetimi-acil-toplanma-alani-sorgulama")
        )
        btn_open_edevlet.pack(side=tk.RIGHT, padx=5)

        # AFAD Preloaded & Local Cached Assembly Areas List
        self.map_tree = ttk.Treeview(f, columns=("Kod", "Ad", "Tur", "Kaynak", "Aciklama"), show="headings", height=10)
        self.map_tree.heading("Kod", text="ID / Kod")
        self.map_tree.heading("Ad", text="Toplanma / Yardım Noktası Adı")
        self.map_tree.heading("Tur", text="Tür")
        self.map_tree.heading("Kaynak", text="Veri Kaynağı")
        self.map_tree.heading("Aciklama", text="Açıklama & Detay")

        self.map_tree.column("Kod", width=90)
        self.map_tree.column("Ad", width=220)
        self.map_tree.column("Tur", width=110)
        self.map_tree.column("Kaynak", width=120)
        self.map_tree.column("Aciklama", width=280)

        self.map_tree.pack(fill=tk.BOTH, expand=True, padx=15, pady=10)

        # Preloaded Points Data
        preloaded = [
            ("AFAD-34-01", "Fatih Parkı AFAD Acil Toplanma Alanı", "Toplanma Alanı", "e-Devlet AFAD", "e-Devlet Onaylı AFAD Deprem Toplanma Parkı"),
            ("AFAD-34-02", "Gülhane Parkı Açık Güvenli Bölge", "Toplanma Alanı", "e-Devlet AFAD", "AFAD İkincil Güvenli Açık Bölge"),
            ("AFAD-34-03", "Yenikapı Etkinlik Alanı Ana Deprem Kriz Merkezi", "Toplanma Alanı", "e-Devlet AFAD", "AFAD Bölgesel Çadır Kent ve Lojistik Depo"),
            ("AFAD-MED-1", "Kızılay Sahra Hastanesi & Tıbbi Müdahale", "Sahra Hastanesi", "AFAD / Kızılay", "Kızılay Acil Sağlık ve Ambulans İrtibat Noktası"),
            ("AFAD-WAT-1", "Merkez İSKİ Su Tankeri & Aşevi Dağıtım Noktası", "Temiz Su / Gıda", "e-Devlet AFAD", "Aşevi, İçme Suyu ve Mobil Jeneratör Noktası")
        ]
        for item in preloaded:
            self.map_tree.insert("", tk.END, values=item)

    # --- 6. RADAR TAB ---
    def _build_radar_tab(self):
        f = self.tab_radar
        lbl = tk.Label(f, text="BAĞLI ANDROID CİHAZLAR MESH RADARI", font=("Segoe UI", 12, "bold"), fg="#a6e3a1", bg="#1e1e2e")
        lbl.pack(pady=10)

        self.radar_tree = ttk.Treeview(f, columns=("ID", "Name", "IP", "Battery", "SOS"), show="headings", height=12)
        self.radar_tree.heading("ID", text="Node ID")
        self.radar_tree.heading("Name", text="Cihaz Adı")
        self.radar_tree.heading("IP", text="IP Adresi")
        self.radar_tree.heading("Battery", text="Pil")
        self.radar_tree.heading("SOS", text="SOS Durumu")

        self.radar_tree.column("ID", width=120)
        self.radar_tree.column("Name", width=180)
        self.radar_tree.column("IP", width=140)
        self.radar_tree.column("Battery", width=80)
        self.radar_tree.column("SOS", width=180)

        self.radar_tree.pack(fill=tk.BOTH, expand=True, padx=15, pady=10)

    # --- Callbacks ---
    def _on_packet_received(self, packet):
        if packet.type == "CHAT_TEXT":
            self.root.after(0, lambda: self._append_chat(f"[{packet.senderName}] {packet.payload}"))
        elif packet.type == "AUDIO_STREAM":
            self._play_pcm_chunk(packet.payload)

    def _on_peer_updated(self, peers):
        def update_ui():
            self.status_lbl.config(text=f"ID: {self.engine.node_id} | Bağlı Cihaz: {len(peers)}")
            for item in self.radar_tree.get_children():
                self.radar_tree.delete(item)
            for p_id, p in peers.items():
                self.radar_tree.insert("", tk.END, values=(p_id, p['name'], p['ip'], f"%{p['battery']}", p['sosStatus'] or "Normal"))

        self.root.after(0, update_ui)

    def _on_video_frame(self, sender_id, base64_frame):
        try:
            buf = BytesIO(base64.b64decode(base64_frame))
            img = Image.open(buf)
            img = img.resize((360, 270), Image.Resampling.LANCZOS)
            img_tk = ImageTk.PhotoImage(img)

            def update_canvas():
                self.video_canvas.config(image=img_tk, text="")
                self.video_canvas.image = img_tk

            self.root.after(0, update_canvas)
        except Exception as e:
            pass

    def _on_sos_alert(self, packet):
        if packet.sosStatus and "GÜVENDE" in packet.sosStatus.upper():
            pass
        else:
            try:
                winsound.Beep(2000, 300)
            except:
                pass

        def update_sos_text():
            self.sos_txt.config(state=tk.NORMAL)
            self.sos_txt.insert("1.0", f"🚨 BİLDİRİM: {packet.senderName} ({packet.sosStatus or packet.payload})\n")
            self.sos_txt.config(state=tk.DISABLED)

        self.root.after(0, update_sos_text)

if __name__ == "__main__":
    root = tk.Tk()
    app = AfetMeshDesktopApp(root)
    root.mainloop()
