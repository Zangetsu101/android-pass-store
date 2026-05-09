function ScreenClone() {
  const T = React.useContext(ThemeContext);
  return (
    <Phone>
      <div style={{padding:"16px 20px 20px", display:"flex", flexDirection:"column", gap:0}}>
        {/* step */}
        <div style={{display:"flex", alignItems:"center", justifyContent:"space-between", marginBottom:18}}>
          <div style={{color:T.textDim, fontSize:9, letterSpacing:"0.1em"}}>SETUP  ·  1 / 2</div>
          <div style={{display:"flex", gap:4}}>
            {[1,2].map(i => (
              <div key={i} style={{
                width: 20, height:3, borderRadius:4,
                background: T.accent,
                opacity: i===1 ? 1 : 0.3,
              }}/>
            ))}
          </div>
        </div>

        <div style={{color:T.accent, fontSize:16, fontWeight:700, marginBottom:4}}>clone store</div>
        <div style={{color:T.textDim, fontSize:10, marginBottom:20, lineHeight:1.6}}>
          point to your existing pass git repository
        </div>

        {/* git url */}
        <Label>git remote url</Label>
        <div style={{
          background:T.surface, border:`1px solid ${T.border2}`,
          borderRadius:T.r, padding:"10px 12px", marginBottom:16,
          display:"flex", alignItems:"center", gap:6,
        }}>
          <span style={{color:T.textDim, fontSize:10}}>git@</span>
          <span style={{color:T.text, fontSize:11}}>github.com:user/pass-store</span>
          <span style={{width:6,height:13,background:T.accent,display:"inline-block",animation:"blink 1s step-end infinite"}}/>
        </div>

        {/* ssh key section */}
        <Label>ssh public key</Label>
        <div style={{
          background:T.surface, border:`1px solid ${T.border2}`,
          borderRadius:T.r, padding:"10px 12px", marginBottom:8,
        }}>
          <div style={{color:T.textDim, fontSize:9, marginBottom:6}}>ed25519 · generated on device</div>
          <div style={{
            color:T.text, fontSize:9, lineHeight:1.7,
            wordBreak:"break-all", letterSpacing:"0.02em",
          }}>
            ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI<br/>
            Hk9mXvP2qR8wLnJdKoT7yBcAeUfGiMsNpQrV<br/>
            <span style={{color:T.textDim}}>pass-android@device</span>
          </div>
        </div>
        <div style={{display:"flex", gap:6, marginBottom:20}}>
          <Btn style={{flex:1, fontSize:9}}>copy key</Btn>
          <Btn dim style={{flex:1, fontSize:9}}>regenerate</Btn>
        </div>

        <div style={{
          background:`${T.warn}12`, border:`1px solid ${T.warn}44`,
          borderRadius:T.r, padding:"8px 12px", marginBottom:20,
          color:`${T.warn}cc`, fontSize:9, lineHeight:1.6,
        }}>
          ⚠ github → Settings → SSH and GPG Keys → New SSH Key
        </div>

        <Btn full>$ git clone</Btn>
      </div>
    </Phone>
  );
}
window.ScreenClone = ScreenClone;
