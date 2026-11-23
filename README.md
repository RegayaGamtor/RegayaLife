# Heart Lifesteal 1.0.0

Plugin survival multiplayer untuk **Paper 1.21.11 (Java 21)**: heart permanen, item **Blood**,
**Relic of Life**, eliminasi otomatis saat heart 0, dan `/heart revive`.

## Cara build
Butuh JDK 21, Maven, dan internet (mengunduh `paper-api`):

    mvn clean package        # atau ./build.sh  /  build.bat

Hasil: `target/HeartLifesteal-1.0.0.jar`. Alternatif tanpa instalasi apa pun: unggah folder ini ke
GitHub, workflow `.github/workflows/build.yml` akan membuild jar otomatis (tab Actions > Artifacts).

## Cara pasang
1. Letakkan `HeartLifesteal-1.0.0.jar` di folder `plugins/` server Paper 1.21.x.
2. Restart server. File dibuat otomatis di `plugins/HeartLifesteal/`:
   `config.yml`, `messages.yml`, `recipe.yml`, `data.db` (SQLite).
3. Ubah pengaturan lalu `/heart reload` (bagian `database` butuh restart).

## Cara kerja singkat
- 1 heart = 2 HP. Setiap perubahan heart langsung diterapkan ke atribut `MAX_HEALTH`.
- Pemain mati: -1 heart permanen. Pembunuh pemain yang valid mendapat 1 **Blood**
  (cooldown anti-farming per pasangan pembunuh-korban, default 300 detik).
- **Blood**: klik kanan, +1 heart (tidak terpakai bila heart sudah penuh).
- **Relic of Life**: klik kanan, heart langsung penuh. Resep di `recipe.yml`
  (default: 4 Blood + 4 Diamond Block + 1 Nether Star).
- Heart 0: pemain tereliminasi dan ditolak login (ban milik plugin, tidak menyentuh banlist server,
  jadi ban manual tidak pernah ikut terhapus). `/heart revive` mengembalikan dengan 1 heart.

## Command
| Command | Fungsi | Permission |
|---|---|---|
| `/heart` | Lihat heart sendiri | `heartlifesteal.use` (default semua) |
| `/heart top` | Leaderboard | `heartlifesteal.top` (default semua) |
| `/heart info <pemain>` | Heart pemain lain | `heartlifesteal.info` (default semua) |
| `/heart set <pemain> <jumlah>` | Atur heart | `heartlifesteal.admin.set` |
| `/heart add <pemain> <jumlah>` | Tambah (maks sesuai batas) | `heartlifesteal.admin.add` |
| `/heart remove <pemain> <jumlah>` | Kurangi | `heartlifesteal.admin.remove` |
| `/heart revive <pemain>` | Hidupkan kembali (1 heart) | `heartlifesteal.admin.revive` |
| `/heart reset <pemain>` | Kembali ke heart default | `heartlifesteal.admin.reset` |
| `/heart give <pemain> <blood\|relic> <jumlah>` | Beri item | `heartlifesteal.admin.give` |
| `/heart history <pemain>` | Riwayat eliminasi | `heartlifesteal.admin.history` |
| `/heart reload` | Muat ulang config | `heartlifesteal.admin.reload` |

`heartlifesteal.admin` memberi semua permission admin (default: OP).

## Catatan penting
- **Semua kematian** mengurangi heart (sesuai contoh "mati 1 kali -> 9 heart"). Ingin hanya mati karena
  pemain lain? Set `lifesteal.lose-heart-on-non-pvp-death: false`.
- Pembunuh ditentukan oleh `Player#getKiller()` (pemain terakhir yang melukai korban beberapa detik terakhir).
  Bunuh diri, lingkungan murni, dan NPC (Citizens) tidak menghasilkan Blood.
- HUD memakai scoreboard sidebar sendiri dan menggantikan scoreboard plugin lain; matikan di `hud.enabled`.
- Plugin RPG yang mengatur max health: set `settings.sync-interval: 0`. Potion Health Boost diblokir
  (`settings.block-health-boost`).
- MySQL: untuk satu server (data di-cache di memori). SQLite/MySQL driver sudah dibawa Paper.
- Item plugin tidak bisa dipakai sebagai bahan crafting lain, dan Relic tidak bisa jadi bahan apa pun.

## Struktur
    src/main/java/id/heartlifesteal/
      HeartLifestealPlugin.java   command/   config/   data/   hud/   item/   listener/   service/   util/
    src/main/resources/ plugin.yml  config.yml  messages.yml  recipe.yml
