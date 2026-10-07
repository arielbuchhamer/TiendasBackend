# Despliegue en Oracle Cloud (Always Free) + Coolify

Alternativa a Railway para alojar varias tiendas en **un único servidor gratuito**, con un panel parecido a Railway.
Sigue valiendo la regla del proyecto: **una instancia de la API + una base PostgreSQL por tienda**, mismo código,
distinta configuración.

```
Cliente ──► Cloudflare (CDN, caché de imágenes, DDoS, oculta la IP)
              │  solo IPs de Cloudflare pueden entrar al servidor
              ▼
   VM Oracle ARM (4 OCPU, 24 GB) — Ubuntu 24.04
   └─ Coolify
       ├─ Traefik (HTTPS, enruta por dominio)
       ├─ Tienda A: API (Dockerfile) + PostgreSQL ── red Docker propia
       ├─ Tienda B: API + PostgreSQL               ── red Docker propia
       └─ Tienda C: API + PostgreSQL               ── red Docker propia
                         │ backup diario
                         ▼
                 Cloudflare R2 (fuera de Oracle)
```

| | |
|---|---|
| Costo | US$ 0 dentro de los límites Always Free (verificar límites vigentes en la web de Oracle) |
| Latencia desde Argentina | ~30–50 ms (región São Paulo o Santiago) vs ~140 ms en EE. UU. |
| Capacidad | Holgada para 6–10 tiendas chicas (cada API ~400 MB + Postgres ~150 MB) |
| Riesgo principal | Las tiendas comparten la máquina: si cae, caen todas. Se mitiga con backups fuera de Oracle y poder levantar todo en otro lado (Railway o un VPS) con las mismas variables |

---

## 1. Cuenta de Oracle Cloud

1. Crear la cuenta en cloud.oracle.com. La **región de origen (home region) no se puede cambiar** y los recursos
   gratuitos solo existen ahí: elegir **Brazil East (São Paulo)** o **Chile Central (Santiago)**.
2. **Pasar la cuenta a Pay As You Go** (Billing → Upgrade). Se sigue sin pagar mientras se usen solo recursos
   Always Free, pero:
   - Oracle deja de reclamar instancias "ociosas" (en cuentas gratuitas puede apagarlas si pasan una semana con
     poco uso, algo habitual en tiendas chicas);
   - mejora la disponibilidad de instancias ARM ("Out of capacity" es frecuente en cuentas gratuitas).
3. **Alerta de presupuesto**: Billing → Budgets → presupuesto de US$ 1 con aviso por mail al 1 %. Si algo empieza a
   cobrar, te enterás enseguida.
4. Activar **MFA** en el usuario administrador de Oracle.

## 2. Máquina virtual

Compute → Instances → Create instance:

| Campo | Valor |
|---|---|
| Image | Canonical **Ubuntu 24.04** (aarch64) |
| Shape | **VM.Standard.A1.Flex** — 4 OCPU, 24 GB RAM (el máximo gratuito) |
| Boot volume | 100–150 GB (el límite gratuito total de bloque es 200 GB) |
| Red | VCN nueva con subred pública, IP pública asignada |
| SSH | Subir **tu clave pública** (nunca contraseña) |

Después de crearla: Networking → IP Administration → convertir la IP pública en **Reserved** (si no, cambia al
recrear la VM y hay que tocar el DNS).

> Las imágenes Docker del proyecto (`eclipse-temurin:21-*-alpine`) y PostgreSQL tienen versión ARM64: el
> `Dockerfile` funciona sin cambios.

## 3. Firewall (dos capas)

**Capa 1 — Security List de la VCN (la principal).** Es un firewall fuera de la VM: Docker no puede saltearlo
(a diferencia de `ufw`, que Docker ignora). Networking → VCN → Security Lists → Ingress rules:

| Puerto | Origen | Para qué |
|---|---|---|
| 22/tcp | **tu IP**/32 | SSH |
| 8000/tcp | **tu IP**/32 | Panel de Coolify, solo durante la instalación (después se borra, paso 5) |
| 80/tcp, 443/tcp | **rangos de Cloudflare** (cloudflare.com/ips), una regla por rango IPv4 | Tráfico de las tiendas |

Borrar cualquier otra regla de ingreso (la regla por defecto abre el 22 a todo internet).
Si tu IP de casa cambia, actualizar la regla del 22 (o usar Tailscale y cerrar el 22 por completo).

**Capa 2 — iptables de la imagen de Oracle.** Las imágenes Ubuntu de Oracle traen reglas que rechazan todo salvo
el 22. Hay que abrir 80, 443 y (temporalmente) 8000:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8000 -j ACCEPT
sudo netfilter-persistent save
```

## 4. Endurecer el servidor

```bash
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y unattended-upgrades fail2ban
sudo dpkg-reconfigure -plow unattended-upgrades       # parches de seguridad automáticos

# SSH: solo clave, sin root
sudo sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/; s/^#\?PermitRootLogin .*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

Activar reinicio automático en una ventana de madrugada cuando un parche lo requiera
(`/etc/apt/apt.conf.d/50unattended-upgrades`):

```
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "05:00";
```

Antes de cerrar la sesión, probar en **otra terminal** que el SSH con clave sigue funcionando.

## 5. Instalar Coolify

```bash
curl -fsSL https://cdn.coollabs.io/coolify/install.sh | sudo bash
```

1. Entrar a `http://<ip>:8000` **en seguida** y crear el usuario administrador (el primero que entra lo crea).
   Contraseña larga + activar **2FA** en el perfil.
2. Settings → configurar un dominio para el panel (ej. `https://coolify.midominio.com`), con DNS en Cloudflare.
   Coolify saca el certificado y el panel queda en HTTPS por el 443.
3. Borrar la regla del puerto **8000** en la Security List y en iptables
   (`sudo iptables -D INPUT -p tcp --dport 8000 -m state --state NEW -j ACCEPT && sudo netfilter-persistent save`).
4. Settings → Updates: dejar las actualizaciones de Coolify en manual y actualizar desde el panel cada tanto,
   después de leer el changelog (una actualización rota del panel no debería sorprender en producción).
5. Conectar GitHub: Sources → GitHub App (acceso **solo** al repositorio TiendasBackend).
6. Storages → agregar **Cloudflare R2** (S3 compatible) para los backups: bucket dedicado `tiendas-backups`
   con token de R2 restringido a ese bucket. No es el bucket de imágenes de ninguna tienda.

## 6. Agregar una tienda

Por cada tienda (ej. `mitienda`):

### 6.1 Red propia
Servers → localhost → Destinations → **Add** → red `tienda-mitienda`. Así la API de una tienda no ve la base de
otra (en la red por defecto `coolify` todos los contenedores se ven entre sí).

### 6.2 Proyecto y base
1. Projects → **New project** `mitienda` (entorno `production`).
2. Add resource → **PostgreSQL** (versión 18), destino `tienda-mitienda`.
   - **No** activar "Make it publicly available": la base solo se accede por la red interna.
   - Usuario y contraseña: los genera Coolify (dejarlos así).
   - Pestaña **Backups**: programar `0 4 * * *` (todos los días 04:00), guardar en el storage R2, retener 14 días.
     Hacer un backup manual y verificar que aparece en R2.
3. Copiar los datos de la conexión **interna** (host = nombre del contenedor, puerto 5432) para el paso siguiente.

### 6.3 API
Add resource → **Private repository (GitHub App)** → TiendasBackend, destino `tienda-mitienda`:

| Campo | Valor |
|---|---|
| Branch | `main` |
| Build pack | **Dockerfile** |
| Commit SHA | El commit del tag que corre esta tienda (`git rev-list -n 1 v1.3.0`), para que cada tienda se actualice cuando vos decidas. Sin fijarlo, cada push a `main` la redeploya |
| Domains | `https://api.mitienda.com.ar` |
| Port | `8080` |
| Health check | Path `/actuator/health/readiness`, puerto 8080, start period 120 s |
| Resource limits | Memoria **640 MB** (límite duro: si la API se desborda no afecta a las otras tiendas) |

Variables de entorno (Environment Variables; la lista completa está en `.env.example`):

```bash
# Rendimiento: heap acotado (la JVM usaría el 75 % de la RAM de la máquina) y pool chico
JAVA_TOOL_OPTIONS=-Xmx320m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError
DB_POOL_MAX=5

# IP real del cliente para el límite de intentos de login. Seguro porque el firewall (paso 3) solo deja
# entrar a Cloudflare: nadie puede llegar al servidor con este header inventado
TIENDA_PROXY_HEADER_IP_CLIENTE=CF-Connecting-IP

# Base: datos internos del Postgres de esta tienda (paso 6.2)
PGHOST=<nombre-del-contenedor-postgres>
PGPORT=5432
PGDATABASE=postgres
PGUSER=postgres
PGPASSWORD=<la generada por Coolify>

# Tienda, Mercado Pago, almacenamiento S3 (R2, bucket privado propio de la tienda) y módulos:
# igual que en Railway (ver README, "Desplegar una tienda nueva").
```

Marcar como **secret** las variables sensibles (claves, tokens). Deploy y revisar los logs: si falta una variable
obligatoria, la app no arranca y el log dice cuál.

### 6.4 DNS y Cloudflare
1. DNS: registro `A` `api` → IP reservada de la VM, **primero sin proxy (nube gris)**, para que Coolify obtenga el
   certificado de Let's Encrypt.
2. Cuando la API responde en `https://api.mitienda.com.ar/actuator/health`, activar el **proxy (nube naranja)**.
3. SSL/TLS → modo **Full (strict)**. Activar "Always Use HTTPS" y TLS mínimo 1.2.
4. **Cache Rule** (Caching → Cache Rules): si la URI empieza con `/api/v1/imagenes/` → *Eligible for cache*,
   Edge TTL "Use cache-control header". Las rutas de imágenes no tienen extensión y Cloudflare no las cachea por
   defecto; con esta regla las sirve desde Buenos Aires y casi no llegan al servidor.
5. Mercado Pago → Webhooks: igual que en Railway (`https://api.mitienda.com.ar/api/v1/webhooks/mercadopago`).

Si la renovación del certificado falla detrás del proxy, alternativa: generar un **Origin Certificate** de
Cloudflare (15 años) y cargarlo en Traefik desde Coolify.

## 7. Operación

| Tarea | Cómo |
|---|---|
| Actualizar una tienda | Cambiar el *Commit SHA* al del nuevo tag → Deploy. De a una tienda; si falla, volver al SHA anterior |
| Ver logs / reiniciar | Panel de Coolify → recurso → Logs |
| Probar un backup | Una vez por mes: restaurar el último dump de una tienda en una base de prueba (`pg_restore`) |
| Monitoreo | Uptime Kuma (se instala desde Coolify) o UptimeRobot gratis contra `/actuator/health` de cada tienda, con aviso por mail o Telegram |
| Uso de recursos | Coolify → Servers → Metrics. Si la RAM total pasa del 70 %, revisar antes de sumar tiendas |
| Parches del SO | Automáticos (paso 4). Revisar cada tanto `sudo apt list --upgradable` |

## 8. Plan de contingencia

Si Oracle cierra la cuenta o la VM se pierde:
1. Crear un servidor nuevo (otro proveedor o Railway).
2. Por cada tienda: crear Postgres, restaurar el último dump desde R2 y desplegar la API con **las mismas
   variables** (guardar una copia cifrada de las variables de cada tienda fuera de Coolify, por ejemplo en un gestor
   de contraseñas).
3. Cambiar el registro `A` en Cloudflare. Las imágenes no se pierden: están en R2.

Las imágenes y los backups viven fuera de Oracle a propósito: la VM es reemplazable.

## 9. IP real del cliente

El límite de intentos de login por IP necesita la IP real del cliente, que detrás de Cloudflare + Traefik viaja en
headers que un atacante podría intentar falsificar:

- Sin configuración, Tomcat (`forward-headers-strategy: native`) recorre `X-Forwarded-For` de derecha a izquierda
  y solo salta proxies de red interna, así que un valor inventado a la izquierda no sirve.
- Con Cloudflare delante, la conexión llega desde IPs de Cloudflare: por eso se usa
  `TIENDA_PROXY_HEADER_IP_CLIENTE=CF-Connecting-IP`, que Cloudflare siempre sobrescribe.
  **Solo es seguro con el firewall del paso 3** (80/443 abiertos únicamente a Cloudflare). Si se abre el servidor a
  todo internet, hay que borrar esa variable.
