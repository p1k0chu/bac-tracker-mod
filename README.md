<h1>Bac Tracker</h1>
Advancement tracker for <a href="https://www.planetminecraft.com/data-pack/blazeandcave-s-advancements-pack-1-12/">Blazes and Caves Advancement Data pack</a>.<br>
Inspired by <a href="https://github.com/TheTalkingMime/bac-tracker">TheTalkingMime/bac-tracker</a>, reused some .csv files from this repo.<br>
Tracker <a href="https://docs.google.com/spreadsheets/d/1Gyp1atdQ7QLEWRHBQ2AQFaTcg38jzZFPvaCOE4OeJhI/edit">template</a> is also created by Mime

<h1>Installation process</h1>
<ol>
    <li>Download latest release from <a href="https://github.com/p1k0chu/bac-tracker">releases page</a>.
        <ul>
            <li>And download these mods:
                <ul>
                    <li><a href="https://modrinth.com/mod/fabric-api">Fabric API</a></li>
                    <li><a href="https://modrinth.com/mod/fabric-language-kotlin">Fabric Language Kotlin</a></li>
                </ul>
            </li>
            <li>Put the mods in your mods folder.</li>
        </ul>
    </li>
    <li>Create an account on <a href="https://console.cloud.google.com">Google Cloud Console.</a>
        <ol>
            <li>Create an account first.</li>
            <li><a href="https://console.cloud.google.com/projectcreate">Create</a> new project.</li>
            <li>Enable <a href="https://console.cloud.google.com/marketplace/product/google/sheets.googleapis.com">Google Sheet API</a>.</li>
            <li>Enable <a href="https://console.cloud.google.com/marketplace/product/google/drive.googleapis.com">Google Drive API</a>.</li>
            <li>Go to <a href="https://console.cloud.google.com/apis/credentials">Credentials page</a> and create new <b>Service Account</b>
                <ul>
                    <li>Data you enter here doesn't really matter</li>
                    <li>You will download credentials JSON file. <b>Keep it. Do not share it</b></li>
                </ul>
            </li>
        </ol>
    </li>
    <li>Copy <a href="https://docs.google.com/spreadsheets/d/1Gyp1atdQ7QLEWRHBQ2AQFaTcg38jzZFPvaCOE4OeJhI/edit">1.21 Template</a> to your google disk
        <ul>
            <li>Copy of template will be in releases for every version, for future generations (you can use this if you want to play on this version in future)</li>
            <li>Copy the id of your sheet. It's in the link of your sheet (address bar), https://docs.google.com/spreadsheets/d/&lt;ID_HERE&gt;/edit<br>
                Example link: https://docs.google.com/spreadsheets/d/1Gyp1atdQ7QLEWRHBQ2AQFaTcg38jzZFPvaCOE4OeJhI/edit<br>
                Example id: 1Gyp1atdQ7QLEWRHBQ2AQFaTcg38jzZFPvaCOE4OeJhI
            </li>
            <li>Copy "client-email" from credentials file and share your new sheet with this email, with editor permissions</li>
        </ul>
    </li>
    <li>Run Minecraft once and open your world. This will create <i>.minecraft/config</i> folder as well as <i>settings.json</i> inside your world folder
        <ul>
            <li>(Optional) Close minecraft</li>
            <li>You will see exact file paths in logs you need to edit</li>
            <li>Put your credentials you downloaded at the path you get in logs</li>
            <li>Open settings.json and paste the sheet id in it</li>
            <li>If you didn't close mc you can run <code>/tracker reload</code> to reload settings</li>
        </ul>
    </li>
    <li>Done. Report issues on this repo's issue tracker</li>
</ol>


<h6>License</h6>
<a property="dct:title" rel="cc:attributionURL" href="http://example.com">Bac Tracker</a> by <a rel="cc:attributionURL dct:creator" property="cc:attributionName" href="https://github.com/p1k0chu">p1k0chu</a> is licensed under <a href="https://creativecommons.org/licenses/by-sa/4.0/?ref=chooser-v1" target="_blank" rel="license noopener noreferrer" style="display:inline-block;">CC BY-SA 4.0<img style="height:17px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/cc.svg?ref=chooser-v1" alt=""><img style="height:17px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/by.svg?ref=chooser-v1" alt=""><img style="height:17px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/sa.svg?ref=chooser-v1" alt=""></a>
