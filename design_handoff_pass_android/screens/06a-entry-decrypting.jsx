function ScreenEntryDecrypting() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <style>{`
        @keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
        @keyframes spin { from{transform:rotate(0deg)} to{transform:rotate(360deg)} }
      `}</style>
      <div style={{ display:"flex", flexDirection:"column", height:"100%" }}>
        {/* topbar */}
        <div style={{
          padding:"12px 18px 10px", borderBottom:`1px solid ${T.border}`,
          display:"flex", alignItems:"center", gap:10, flexShrink:0,
        }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M9 11L5 7L9 3" stroke={T.textDim} strokeWidth="1.4" strokeLinecap="round"/>
          </svg>
          <div style={{ flex:1 }}>
            <div style={{ color:T.textDim, fontSize:9 }}>email/gmail</div>
            <div style={{ color:T.accent, fontSize:14, fontWeight:700 }}>gmail</div>
          </div>
        </div>

        <div style={{ flex:1, overflowY:"auto", padding:"16px 18px" }}>
          {/* password card — decrypting state */}
          <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>password</div>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, padding:"14px 14px", marginBottom:6,
            display:"flex", flexDirection:"column", alignItems:"center", gap:10,
          }}>
            <div style={{ display:"flex", alignItems:"center", gap:10, padding:"4px 0", width:"100%" }}>
              <div style={{
                width: 16, height: 16, borderRadius:"50%",
                border:`2px solid ${T.border2}`,
                borderTopColor: T.accent,
                animation:"spin 0.8s linear infinite", flexShrink:0,
              }}/>
              <span style={{ color:T.textDim, fontSize:10 }}>decrypting…</span>
            </div>
            <div style={{ display:"flex", gap:6, width:"100%" }}>
              <Shimmer height={30} radius={T.r} style={{flex:1}}/>
              <Shimmer height={30} radius={T.r} style={{flex:1}}/>
            </div>
          </div>
          <div style={{ color:T.textFaint, fontSize:9, marginBottom:20 }}>decrypted in-memory · auto-clears in 45s</div>

          {/* metadata — always visible */}
          <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>metadata</div>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, overflow:"hidden", marginBottom:16,
          }}>
            {[["path","email/gmail"],["modified","2024-11-03"],["git commit","a3f92b1"]].map(([k,v],i,arr)=>(
              <div key={k} style={{ display:"flex", padding:"9px 14px", borderBottom:i<arr.length-1?`1px solid ${T.border}`:"none" }}>
                <span style={{ color:T.textDim, fontSize:10, width:72, flexShrink:0 }}>{k}</span>
                <span style={{ color:T.text, fontSize:10 }}>{v}</span>
              </div>
            ))}
          </div>

          {/* notes shimmer */}
          <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>notes</div>
          <div style={{
            background:T.surface, border:`1px solid ${T.border2}`,
            borderRadius:T.r, padding:"12px 14px", display:"flex", flexDirection:"column", gap:8, minHeight:60,
          }}>
            <Shimmer width="85%" height={10}/>
            <Shimmer width="60%" height={10}/>
          </div>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenEntryDecrypting = ScreenEntryDecrypting;
