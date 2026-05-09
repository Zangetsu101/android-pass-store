function ScreenEntry() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{display:"flex", flexDirection:"column", height:"100%"}}>
        {/* topbar */}
        <div style={{
          padding:"12px 18px 10px",
          borderBottom:`1px solid ${T.border}`,
          display:"flex", alignItems:"center", gap:10,
          flexShrink:0,
        }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 11L5 7L9 3" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
          <div style={{flex:1}}>
            <div style={{color:T.textDim, fontSize:9}}>email/gmail</div>
            <div style={{color:T.accent, fontSize:14, fontWeight:700}}>gmail</div>
          </div>
        </div>

        <div style={{flex:1, overflowY:"auto", padding:"16px 18px"}}>
          {/* password */}}
          <Label>password</Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, padding:"12px 14px", marginBottom:6,
          }}>
            <div style={{
              color:T.accent, fontSize:13, fontWeight:500,
              letterSpacing:"0.1em", filter:"blur(5px)",
              userSelect:"none", marginBottom:8,
            }}>P@ssw0rd!Secure2024</div>
            <div style={{display:"flex", gap:6}}>
              <div style={{
                flex:1, background:T.accentDim, border:`1px solid ${T.accentMid}`,
                borderRadius:T.r-1, padding:"6px 10px",
                color:T.accent, fontSize:9, textAlign:"center", fontWeight:600,
              }}>copy</div>
              <div style={{
                flex:1, background:T.surface, border:`1px solid ${T.border2}`,
                borderRadius:T.r-1, padding:"6px 10px",
                color:T.textDim, fontSize:9, textAlign:"center",
              }}>reveal</div>
            </div>
          </div>
          <div style={{color:T.textFaint, fontSize:9, marginBottom:18}}>
            decrypted in-memory · auto-clears in 45s
          </div>

          {/* notes */}
          <Label>notes</Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, padding:"12px 14px", marginBottom:16,
            minHeight:60,
          }}>
            <div style={{color:T.textDim, fontSize:11, lineHeight:1.7}}>
              recovery: alice+recover@gmail.com<br/>
              <span style={{color:T.textFaint}}>2fa via backup codes in bitwarden</span>
            </div>
          </div>

          {/* metadata */}
          <Label>metadata</Label>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, overflow:"hidden", marginBottom:16,
          }}>
            {[
              ["path", "email/gmail"],
              ["modified", "2024-11-03"],
              ["git commit", "a3f92b1"],
            ].map(([k,v],i,arr) => (
              <div key={k} style={{
                display:"flex", padding:"9px 14px",
                borderBottom: i<arr.length-1 ? `1px solid ${T.border}` : "none",
              }}>
                <span style={{color:T.textDim, fontSize:10, width:72, flexShrink:0}}>{k}</span>
                <span style={{color:T.text, fontSize:10}}>{v}</span>
              </div>
            ))}
          </div>

          {/* actions */}

        </div>
      </div>
    </Phone>
  );
}
window.ScreenEntry = ScreenEntry;
