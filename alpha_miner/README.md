# Alpha Miner

This example shows how `AlphaMinerPlugin` from ProMs repository can be used inside Apache Beam pipeline.

## Configuration

To add ProMs library to the ivy build system changes in `ivysettings.xml` had to be made. Following resolvers were added, and appended to default chain:
```
<url name="prom">
  <ivy pattern="https://svn.win.tue.nl/repos/[organisation]/Releases/Packages/[module]/[revision]/ivy.xml" />
  <artifact pattern="https://svn.win.tue.nl/repos/[organisation]/Releases/Packages/[module]/[revision]/[artifact]-[revision].[ext]" />
</url>
<url name="prom-libs">
  <ivy pattern="https://svn.win.tue.nl/repos/prom/Libraries/[module]/[revision]/ivy.xml" />
  <artifact pattern="https://svn.win.tue.nl/repos/prom/Libraries/[module]/[revision]/[artifact]-[revision].[ext]" />
  <artifact pattern="https://svn.win.tue.nl/repos/prom/Libraries/[module]/[revision]/[artifact]_[revision].[ext]" />
</url>
```

After doing so dependencies from ProMs repo can be listed in `ivy.xml` file as follows:
```
<dependency org="prom" name="PetriNets" rev="latest" changing="true" transitive="true" />
<dependency org="prom" name="AlphaMiner" rev="latest" changing="true" transitive="true" />
<dependency org="prom-libs" name="OpenXES" rev="20171212" changing="true" transitive="true" />
```
