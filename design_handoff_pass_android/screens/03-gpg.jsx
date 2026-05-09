function ScreenGpg() {
  const T = React.useContext(ThemeContext);
  const keys = [
    { id:"A3F9 2B1C", uid:"alice@example.com", exp:"2026-01", bits:"4096" },
    { id:"D71E 0FA8", uid:"alice@work.io", exp:"2025-08", bits:"3072" },
  ];
  return (
    <Phone>
      <div style={{padding:"16px 20px 20px"}}>
        <div style={{display:"flex", alignItems:"center", justifyContent:"space-between", marginBottom:18}}>
          <div style={{color:T.textDim, fontSize:9, letterSpacing:"0.1em"}}>SETUP  ·  2 / 2</div>
          <div style={{display:"flex", gap:4}}>
            {[1,2].map(i=>(
              <div key={i} style={{width:20,height:3,borderRadius:4,background:T.accent,opacity:i<2?0.6:1}}/>
            ))}
          </div>
        </div>

        <div style={{color:T.accent, fontSize:16, fontWeight:700, marginBottom:4}}>gpg key</div>
        <div style={{color:T.textDim, fontSize:10, marginBottom:20, lineHeight:1.6}}>
          provide the keypair used to encrypt/decrypt your store
        </div>

        {/* import from file */}
        <div style={{
          background:T.surface, border:`1px solid ${T.border2}`,
          borderRadius:T.r, padding:"12px 14px", marginBottom:10,
          display:"flex", alignItems:"center", gap:10, cursor:"pointer",
        }}>
          <div style={{
            width:28, height:28, borderRadius:T.r,
            background:T.accentDim, border:`1px solid ${T.accentMid}`,
            display:"flex", alignItems:"center", justifyContent:"center",
            flexShrink:0,
          }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <rect x="2" y="1" width="10" height="12" rx="1.5" stroke={T.accent} strokeWidth="1.2"/>
              <line x1="4.5" y1="5" x2="9.5" y2="5" stroke={T.accent} strokeWidth="1.1" strokeLinecap="round"/>
              <line x1="4.5" y1="7.5" x2="9.5" y2="7.5" stroke={T.accent} strokeWidth="1.1" strokeLinecap="round"/>
              <line x1="4.5" y1="10" x2="7.5" y2="10" stroke={T.accent} strokeWidth="1.1" strokeLinecap="round"/>
            </svg>
          </div>
          <div>
            <div style={{color:T.text, fontSize:11}}>import from file</div>
            <div style={{color:T.textDim, fontSize:9, marginTop:2}}>.asc / .gpg secret key file</div>
          </div>
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" style={{marginLeft:"auto"}}>
            <path d="M3.5 8L6.5 5L3.5 2" stroke={T.textFaint} strokeWidth="1.2" strokeLinecap="round"/>
          </svg>
        </div>

        {/* divider */}
        <div style={{display:"flex", alignItems:"center", gap:8, margin:"4px 0 10px"}}>
          <div style={{flex:1, height:1, background:T.border}}/>
          <span style={{color:T.textFaint, fontSize:9}}>or</span>
          <div style={{flex:1, height:1, background:T.border}}/>
        </div>

        {/* paste armored key */}
        <Label>paste armored secret key</Label>
        <div style={{
          background:T.surface, border:`1px solid ${T.border2}`,
          borderRadius:T.r, padding:"10px 12px", marginBottom:6,
          minHeight:80,
        }}>
          <div style={{color:T.textFaint, fontSize:10, lineHeight:1.7}}>
            -----BEGIN PGP PRIVATE KEY BLOCK-----<br/>
            <span style={{color:T.textFaint, opacity:0.4}}>paste key here…</span>
          </div>
        </div>
        <div style={{color:T.textFaint, fontSize:9, marginBottom:20}}>
          exported via: gpg --armor --export-secret-keys your@email.com
        </div>

        <Btn full>finish setup →</Btn>
      </div>
    </Phone>
  );
}
window.ScreenGpg = ScreenGpg;
