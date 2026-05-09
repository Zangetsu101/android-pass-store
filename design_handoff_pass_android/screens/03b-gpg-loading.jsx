function ScreenGpgLoading() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <style>{`@keyframes shimmer{0%{background-position:200% 0}100%{background-position:-200% 0}}`}</style>
      <div style={{ padding:"16px 20px 20px" }}>
        <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between", marginBottom:18 }}>
          <div style={{ color:T.textDim, fontSize:9, letterSpacing:"0.1em" }}>SETUP  ·  2 / 2</div>
          <div style={{ display:"flex", gap:4 }}>
            {[1,2].map(i=>(
              <div key={i} style={{ width:20, height:3, borderRadius:4, background:T.accent, opacity:i===2?1:0.6 }}/>
            ))}
          </div>
        </div>

        <div style={{ color:T.accent, fontSize:16, fontWeight:700, marginBottom:4 }}>gpg key</div>
        <div style={{ color:T.textDim, fontSize:10, marginBottom:20, lineHeight:1.6 }}>
          provide the keypair used to encrypt/decrypt your store
        </div>

        <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>import</div>

        {/* skeleton import row */}
        <div style={{
          background:T.surface, border:`1px solid ${T.border2}`,
          borderRadius:T.r, padding:"12px 14px", marginBottom:10,
          display:"flex", alignItems:"center", gap:10,
        }}>
          <div style={{ width:28, height:28, borderRadius:T.r, background:T.border2, flexShrink:0 }}/>
          <div style={{ flex:1, display:"flex", flexDirection:"column", gap:6 }}>
            <Shimmer width="55%" height={10}/>
            <Shimmer width="70%" height={9}/>
          </div>
        </div>

        <div style={{ display:"flex", alignItems:"center", gap:8, margin:"4px 0 10px" }}>
          <div style={{ flex:1, height:1, background:T.border }}/>
          <span style={{ color:T.textFaint, fontSize:9 }}>or</span>
          <div style={{ flex:1, height:1, background:T.border }}/>
        </div>

        <div style={{ color:T.accent, fontSize:9, letterSpacing:"0.12em", textTransform:"uppercase", marginBottom:6, fontWeight:600 }}>paste armored key</div>
        <div style={{
          background:T.surface, border:`1px solid ${T.border2}`,
          borderRadius:T.r, padding:"10px 12px", marginBottom:20,
          minHeight:80, display:"flex", flexDirection:"column", gap:7,
        }}>
          <Shimmer width="90%" height={9}/>
          <Shimmer width="40%" height={9}/>
        </div>
      </div>
    </Phone>
  );
}
window.ScreenGpgLoading = ScreenGpgLoading;
