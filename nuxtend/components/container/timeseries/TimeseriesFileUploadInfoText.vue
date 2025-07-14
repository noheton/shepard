<script setup lang="ts"></script>
<template>
  <div>
    <p>The CSV file must contain the following columns and format:</p>
    <p class="font-weight-bold">
      DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE
    </p>
    <br >
    <p>TIMESTAMP is nanoseconds since epoch.</p>
    <br >
    <p>For example:</p>
    <p>DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE</p>
    <p>MyDev,value,MyLoc,MyMeas,MySymname,1708339761809582900,0.0</p>
    <p>MyDev,value,MyLoc,MyMeas,MySymName,1708339761809582900,0.017</p>
    <p>...</p>
  </div>
</template>
