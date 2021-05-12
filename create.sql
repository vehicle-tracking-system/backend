create table TRACK
(
    ID         BIGINT auto_increment,
    VEHICLE_ID BIGINT,
    TIMESTAMP  TIMESTAMP not null,
    constraint TRACK_PK
        primary key (ID)
);

create unique index VEHICLE_ID_TIMESTAMP
    on TRACK (VEHICLE_ID, TIMESTAMP);

create table VEHICLE
(
    ID         BIGINT auto_increment,
    NAME       TEXT,
    CREATED_AT TIMESTAMP,
    DELETED_AT TIMESTAMP,
    constraint VEHICLE_PK
        primary key (ID)
);

create table POSITION
(
    ID         BIGINT auto_increment,
    VEHICLE_ID BIGINT,
    SPEED      DECIMAL,
    LATITUDE   DECIMAL(15, 10),
    LONGITUDE  DECIMAL(15, 10),
    TIMESTAMP  TIMESTAMP,
    TRACK_ID   BIGINT,
    SESSION_ID TEXT default 'N/A' not null,
    constraint POSITION_PK
        primary key (ID)
);

create table FLEET
(
    ID   BIGINT auto_increment,
    NAME VARCHAR,
    constraint FLEET_PK
        primary key (ID)
);

create table VEHICLEFLEET
(
    ID         BIGINT auto_increment,
    VEHICLE_ID BIGINT not null
        references VEHICLE (ID)
            on delete cascade,
    FLEET_ID   BIGINT not null
        references FLEET (ID)
            on delete cascade,
    constraint VEHICLEFLEET_PK
        primary key (ID)
);

create unique index FLEET_NAME_UK
    on FLEET (NAME);

create table USER
(
    NAME       TEXT    not null,
    CREATED_AT TIMESTAMP,
    DELETED_AT TIMESTAMP,
    PASSWORD   TEXT,
    USERNAME   VARCHAR not null,
    ROLES      ARRAY,
    ID         INT auto_increment,
    constraint USER_PK
        primary key (ID)
);

create unique index USERNAME_UK
    on USER (USERNAME);

create table TRACKER
(
    ID         BIGINT auto_increment,
    VEHICLE_ID BIGINT
        references VEHICLE (ID)
            on delete cascade,
    NAME       TEXT,
    TOKEN      TEXT,
    CREATED_AT TIMESTAMP,
    DELETED_AT TIMESTAMP,
    constraint VEHICLEUSER_PK
        primary key (ID)
);
