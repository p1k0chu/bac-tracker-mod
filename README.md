<h1>Bac Tracker</h1>
Advancement tracker for <a href="https://bit.ly/3u9BXTr">Blazes and Caves Advancement Data pack</a>.<br>
Inspired by <a href="https://github.com/TheTalkingMime/bac-tracker">TheTalkingMime/bac-tracker</a><br>
Tracker template is originally made by TheTalkingMime and tjthings

Latest tracker <a href="https://docs.google.com/spreadsheets/d/1p0Nl61avCxvLxlIrD6ZAeoyIkEDiTf7C3ZIQTC-Ivew/edit?usp=sharing">template</a> for use with BACAP<br>
You will also find sheet downloads in releases.

You can use this tracker with any other advancement data pack, or with vanilla game, but you would have to make your own sheet with all advancements you need to track

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
    <li>Create an account on <a href="https://console.cloud.google.com">Google Cloud Console.</a><br>
Google will ask you for credit card information, but they won't charge you unless you upgrade your account, and everything you will actually use is free
        <ol>
            <li>Create an account first.</li>
            <li><a href="https://console.cloud.google.com/projectcreate">Create</a> new project.</li>
            <li>Enable <a href="https://console.cloud.google.com/marketplace/product/google/sheets.googleapis.com">Google Sheet API</a>.</li>
            <li>Enable <a href="https://console.cloud.google.com/marketplace/product/google/drive.googleapis.com">Google Drive API</a>.</li>
            <li>Go to <a href="https://console.cloud.google.com/apis/credentials">Credentials page</a> and create new <b>Service Account</b>
                <ul>
                    <li>Data you enter here doesn't really matter</li>
                </ul>
            </li>
            <li>Click on your service account</li>
            <li>Go to KEYS</li>
            <li>Create new JSON key
                <ul>
                    <li>You will download credentials JSON file. <b>Keep it. Do not share it</b><br>
If you leak your key accidentally, you can reset it and create a new one</li>
                </ul>
            </li>
        </ol>
    </li>
    <li>Copy <a href="https://docs.google.com/spreadsheets/d/1p0Nl61avCxvLxlIrD6ZAeoyIkEDiTf7C3ZIQTC-Ivew/edit?usp=sharing">latest template</a> to your Google Drive<br>
This is the latest version of the template. if you want to use earlier version you can find links on release page and copy to your Google Drive.
        <ul>
            <li>Copy the id of your sheet. It's in the link of your sheet (address bar), https://docs.google.com/spreadsheets/d/&lt;ID_HERE&gt;/edit<br>
                Example link: https://docs.google.com/spreadsheets/d/1Gyp1atdQ7QLEWRHBQ2AQFaTcg38jzZFPvaCOE4OeJhI/edit<br>
                Example id: 1Gyp1atdQ7QLEWRHBQ2AQFaTcg38jzZFPvaCOE4OeJhI
            </li>
            <li>Copy "client-email" from credentials file and share your new sheet with this email, with editor permissions</li>
        </ul>
    </li>
    <li>Run Minecraft once and open your world. This will create <i>.minecraft/config</i> folder as well as <i>settings.json</i> inside your world folder
        <ul>
            <li>Put your credentials in <code>.minecraft/config/bac-tracker-mod</code> and rename to credentials.json</li>
            <li>
                Open <code>your_world_folder/tracker/settings.json</code> and paste the sheet id in "spreadsheet-id" field
                <ul>
                <li>
                    If settings file does not exist, make sure to run <code>/tracker reload</code> command, it must generate a settings template for you.
                </li>
                </ul>
            </li>
            <li>If you didn't close mc you can run <code>/tracker reload</code> to reload settings</li>
        </ul>
    </li>
    <li>Done. Report issues on this repo's issue tracker</li>
</ol>


<h6>License</h6>
<a property="dct:title" rel="cc:attributionURL" href="https://github.com/p1k0chu/bac-tracker-mod">Bac Tracker</a> by <a rel="cc:attributionURL dct:creator" property="cc:attributionName" href="https://github.com/p1k0chu">p1k0chu</a> is licensed under <a href="https://creativecommons.org/licenses/by-sa/4.0/?ref=chooser-v1" target="_blank" rel="license noopener noreferrer" style="display:inline-block;">CC BY-SA 4.0<img style="height:17px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/cc.svg?ref=chooser-v1" alt=""><img style="height:17px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/by.svg?ref=chooser-v1" alt=""><img style="height:17px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/sa.svg?ref=chooser-v1" alt=""></a>
